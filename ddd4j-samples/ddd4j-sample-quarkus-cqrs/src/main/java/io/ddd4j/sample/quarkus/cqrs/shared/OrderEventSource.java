package io.ddd4j.sample.quarkus.cqrs.shared;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.data.event.store.jpa.JpaEventStore;
import io.ddd4j.sample.order.application.OutboxMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 原 Outbox ID 幂等中继到已有事件表，真实全局 position 驱动共享 Runner。 */
@ApplicationScoped
public class OrderEventSource implements EventChunkReader<Object> {
    private static final String ORDER_AGGREGATE_TYPE = "Order";

    @Inject EntityManager manager;
    @Inject TransactionSynchronizationRegistry transactions;
    @Inject SharedOrderStorage storage;
    @Inject ObjectMapper mapper;
    private JpaEventStore store() {
        return JpaEventStore.participatingManaged(manager, transactions::setRollbackOnly);
    }
    /** @param message 已提交 Outbox 消息；事件事务先提交，随后才确认 Outbox */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void append(OutboxMessage message) {
        var source = store();
        var id = new OrderStreamId(message.aggregateId());
        var existing = source.read(ORDER_AGGREGATE_TYPE, id);
        if (existing.stream().noneMatch(event -> event.eventId().asString().equals(message.id()))) {
            long version = existing.isEmpty() ? 0 : existing.get(existing.size()-1).version();
            source.append(ORDER_AGGREGATE_TYPE, id, List.of(new OrderOutboxEvent(message.id(), message.aggregateId(),
                    message.eventType(), mapper.valueToTree(message.payload()))), version);
        }
    }
    @Override @Transactional
    public EventChunk<Object> read(String stream, long from, int size, Collection<String> types) {
        List<StoredEvent> events = store().readAll(from, size);
        if (events.isEmpty()) {
            return EventChunk.empty(from);
        }
        List<Object> orderEvents = new ArrayList<>();
        for (StoredEvent event : events) {
            if (ORDER_AGGREGATE_TYPE.equals(event.aggregateType())) {
                orderEvents.add(event);
            }
        }
        // 位点按过滤前的全局页尾推进，整页无订单事件也要消费。
        return new EventChunk<>(orderEvents, events.get(events.size() - 1).position() + 1);
    }
    /**
     * @param id 订单标识
     * @return 持久化事件中保留的原 Outbox 信封
     */
    @Transactional
    public List<OutboxMessage> events(String id) {
        return store().read(ORDER_AGGREGATE_TYPE, new OrderStreamId(id)).stream().map(event -> {
            OrderOutboxEvent payload = (OrderOutboxEvent) event.payload();
            return new OutboxMessage(payload.getOutboxId(), payload.getOrderId(), payload.getOriginalType(),
                    payload.getOriginalPayload(), event.timestamp().toInstant());
        }).toList();
    }
}
