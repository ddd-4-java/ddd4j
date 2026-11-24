package io.ddd4j.sample.spring.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.data.event.store.jpa.JpaEventStore;
import io.ddd4j.data.projection.jpa.EntityManagerProjectionPositionRepository;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderRepository;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 单实例 CQRS 样例中继和投影。Outbox 为本入口独占，未启动 MQ 消费者。
 * JDBC/事件库/检查点使用独立事务；先写后确认，靠原消息 ID 去重及查询 UPSERT 恢复。
 */
public class OrderProjection {
    private static final String STREAM = "spring-orders";
    private final OrderRepository repository;
    private final OutboxPort outbox;
    private final OrderReadModelPort queries;
    private final JdbcOrderTransactionPort transaction;
    private final EntityManagerFactory factory;
    private final ObjectMapper mapper;

    /**
     * @param repository 写仓储
     * @param outbox 独占 Outbox
     * @param queries 查询仓储
     * @param transaction JDBC 事务
     * @param factory 实体工厂
     * @param mapper 原始载荷映射器
     */
    public OrderProjection(OrderRepository repository, OutboxPort outbox, OrderReadModelPort queries,
            JdbcOrderTransactionPort transaction, EntityManagerFactory factory, ObjectMapper mapper) {
        this.repository = repository; this.outbox = outbox; this.queries = queries;
        this.transaction = transaction; this.factory = factory; this.mapper = mapper;
    }

    /** 执行一次有界中继与 source-aware 投影；失败向调用方传播，下一次显式调用重试。 */
    public synchronized void project() {
        relay();
        try (var entityManager = factory.createEntityManager()) {
            var tx = entityManager.getTransaction();
            tx.begin();
            try {
                var store = JpaEventStore.participating(entityManager);
                var service = new DefaultProjectionService(new EntityManagerProjectionPositionRepository(entityManager));
                ProjectionRunner<StoredEvent> runner = new ProjectionRunner<>(service, (stream, from, size, types) -> {
                    List<StoredEvent> events = store.readAll(from, size);
                    if (events.isEmpty()) {
                        return EventChunk.empty(from);
                    }
                    List<StoredEvent> orderEvents = new ArrayList<>();
                    for (StoredEvent event : events) {
                        if ("Order".equals(event.aggregateType())) {
                            orderEvents.add(event);
                        }
                    }
                    // 游标基于原始事件批次，即使整个批次都不是订单事件。
                    return new EventChunk<>(orderEvents, events.get(events.size() - 1).position() + 1);
                });
                runner.runOnce(new ProjectionView<>() {
                    @Override public String getName() { return STREAM; }
                    @Override public String getCron() { return "0/5 * * * * ?"; }
                    @Override public Collection<String> getEventTypes() { return List.of(); }
                    @Override public void handleEvents(Collection<StoredEvent> events) {
                        // 查询写入独立提交；检查点失败后以同一主键 UPSERT 重放，不能回退读取写模型。
                        transaction.execute(() -> {
                            for (StoredEvent event : events) {
                                OrderOutboxEvent envelope = (OrderOutboxEvent) event.payload();
                                queries.project(persisted(envelope.getOrderId()));
                            }
                        });
                    }
                });
                tx.commit();
            } catch (RuntimeException | Error failure) {
                if (tx.isActive()) { tx.rollback(); }
                throw failure;
            }
        }
    }

    /**
     * 独占处理待中继 Outbox；事件写入成功而确认失败时，按持久事件 ID 去重重试。
     * 本样例仅支持单实例串行中继，不将读取后去重声明为多实例并发协议。
     */
    public synchronized void relay() {
        transaction.execute(() -> {
            for (OutboxMessage message : outbox.pending(100)) {
                try (var entityManager = factory.createEntityManager()) {
                    JpaEventStore store = new JpaEventStore(entityManager);
                    var id = new OrderStreamId(message.aggregateId());
                    List<StoredEvent> existing = store.read("Order", id);
                    if (existing.stream().noneMatch(event -> event.eventId().asString().equals(message.id()))) {
                        long version = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).version();
                        store.append("Order", id, List.of(new OrderOutboxEvent(message.id(), message.aggregateId(),
                                message.eventType(), mapper.valueToTree(message.payload()))), version);
                    }
                }
                outbox.markPublished(message.id());
            }
        });
    }

    /**
     * @return 实际持久化的下一源位置
     */
    public long position() {
        try (var entityManager = factory.createEntityManager()) {
            return new DefaultProjectionService(new EntityManagerProjectionPositionRepository(entityManager))
                    .readProjectionPosition(STREAM);
        }
    }
    /**
     * @param orderId 订单标识
     * @return 实际写仓储快照，仅供验收和投影使用
     */
    public OrderReadModel persisted(String orderId) {
        Order order = repository.findById(orderId).orElseThrow();
        return new OrderReadModel(order.id(), order.orderNo(), order.buyerId(), order.buyerName(),
                order.status(), order.totalAmount().amount());
    }
    /**
     * @param orderId 订单标识
     * @return 已提交源事件恢复的原 Outbox 信封
     */
    public List<OutboxMessage> events(String orderId) {
        try (var entityManager = factory.createEntityManager()) {
            return new JpaEventStore(entityManager).read("Order", new OrderStreamId(orderId)).stream().map(event -> {
                OrderOutboxEvent payload = (OrderOutboxEvent) event.payload();
                return new OutboxMessage(payload.getOutboxId(), payload.getOrderId(), payload.getOriginalType(),
                        payload.getOriginalPayload(), event.timestamp().toInstant());
            }).toList();
        }
    }
}
