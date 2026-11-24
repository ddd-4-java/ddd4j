package io.ddd4j.sample.quarkus.cqrs.shared;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.List;
/** 查询先本地提交，JTA 检查点后提交；失败重放依赖已有订单主键 UPSERT。 */
@ApplicationScoped
public class OrderProjection implements ProjectionView<Object> {
    @Inject SharedOrderStorage storage;
    @Override public String getName() { return "quarkus-orders"; }
    @Override public String getCron() { return "0/5 * * * * ?"; }
    @Override public Collection<String> getEventTypes() { return List.of(); }
    @Override public void handleEvents(Collection<Object> events) {
        storage.transaction().execute(() -> {
            for (Object value : events) {
                var event = (OrderOutboxEvent) ((StoredEvent) value).payload();
                storage.queries().project(storage.persisted(event.getOrderId()));
            }
        });
    }
}
