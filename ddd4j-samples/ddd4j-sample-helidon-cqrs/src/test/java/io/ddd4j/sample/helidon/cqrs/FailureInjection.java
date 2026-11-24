package io.ddd4j.sample.helidon.cqrs;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.domain.OrderQuery;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessProducer;
import jakarta.enterprise.inject.spi.Producer;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.context.spi.CreationalContext;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
/** 只在测试 classpath 装饰真实 CDI producer，异常窗口保持产品持久化链路。 */
public class FailureInjection implements Extension {
 static final AtomicBoolean FAIL_QUERY=new AtomicBoolean(), FAIL_ACK=new AtomicBoolean();
 void query(@Observes ProcessProducer<?,OrderReadModelPort> event) {
  Producer<OrderReadModelPort> original=event.getProducer();
  event.setProducer(wrap(original,delegate -> new OrderReadModelPort() {
   @Override public void project(OrderReadModel row) { if(FAIL_QUERY.getAndSet(false)) { throw new IllegalStateException("before query write"); } delegate.project(row); }
   @Override public Optional<OrderReadModel> findProjectionById(String id) { return delegate.findProjectionById(id); }
   @Override public List<OrderReadModel> query(OrderQuery query) { return delegate.query(query); }
  }));
 }
 void outbox(@Observes ProcessProducer<?,OutboxPort> event) {
  Producer<OutboxPort> original=event.getProducer();
  event.setProducer(wrap(original,delegate -> new OutboxPort() {
   @Override public void append(List<OutboxMessage> messages) { delegate.append(messages); }
   @Override public List<OutboxMessage> pending(int limit) { return delegate.pending(limit); }
   @Override public void markFailed(String id,String reason) { delegate.markFailed(id,reason); }
   @Override public void markPublished(String id) { if(FAIL_ACK.getAndSet(false)) { throw new IllegalStateException("after event commit before acknowledgement"); } delegate.markPublished(id); }
  }));
 }
 private <T> Producer<T> wrap(Producer<T> original,java.util.function.Function<T,T> decorate) {
  return new Producer<>() {
   @Override public T produce(CreationalContext<T> context) { return decorate.apply(original.produce(context)); }
   @Override public void dispose(T instance) { original.dispose(instance); }
   @Override public Set<InjectionPoint> getInjectionPoints() { return original.getInjectionPoints(); }
  };
 }
}
