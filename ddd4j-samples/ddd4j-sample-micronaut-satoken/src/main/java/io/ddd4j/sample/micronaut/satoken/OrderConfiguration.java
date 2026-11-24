package io.ddd4j.sample.micronaut.satoken;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
/** 原生认证与共享 Order 内核装配，订单存储仅供内存演示。 */
@Factory
public class OrderConfiguration {
 @Singleton OrderApplicationService orders() {
  var adapters = new InMemoryOrderAdapters();
  return new OrderApplicationService(adapters, adapters, adapters, adapters, adapters);
 }
 @Singleton SubjectProvider subjects() { return new io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider(); }
}
