package io.ddd4j.sample.javalin.cqrs.event;

import io.ddd4j.core.cqrs.readmodel.TypedEventHandler;
import io.ddd4j.sample.javalin.cqrs.order.domain.event.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单领域事件监听器（Javalin 无 DI，手动注册）。
 *
 * <p>本类构造 {@link TypedEventHandler} 列表，业务方在 main 中通过
 * {@code new TypedEventDispatcher(handlers)} 创建分发器。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class OrderEventListener {

    /**
     * 创建订单事件处理器集合。
     *
     * @return 事件处理器列表
     */
    public List<TypedEventHandler<?>> handlers() {
        List<TypedEventHandler<?>> list = new ArrayList<>();
        list.add(new OrderCreatedHandler());
        list.add(new OrderLineAddedHandler());
        list.add(new OrderPaidHandler());
        list.add(new OrderShippedHandler());
        list.add(new OrderCancelledHandler());
        return list;
    }

    /**
     * OrderCreated 事件处理器
     */
    class OrderCreatedHandler implements TypedEventHandler<OrderCreatedEvent> {
        @Override
        public String getEventType() {
            return "OrderCreated";
        }

        @Override
        public Class<OrderCreatedEvent> getEventClass() {
            return OrderCreatedEvent.class;
        }

        @Override
        public void handle(OrderCreatedEvent event) {
            log.info("[OrderEventListener] 订单已创建: orderId={}", event.source());
        }
    }

    /**
     * OrderLineAdded 事件处理器
     */
    class OrderLineAddedHandler implements TypedEventHandler<OrderLineAddedEvent> {
        @Override
        public String getEventType() {
            return "OrderLineAdded";
        }

        @Override
        public Class<OrderLineAddedEvent> getEventClass() {
            return OrderLineAddedEvent.class;
        }

        @Override
        public void handle(OrderLineAddedEvent event) {
            log.info("[OrderEventListener] 订单行已添加: orderId={}", event.source());
        }
    }

    /**
     * OrderPaid 事件处理器
     */
    class OrderPaidHandler implements TypedEventHandler<OrderPaidEvent> {
        @Override
        public String getEventType() {
            return "OrderPaid";
        }

        @Override
        public Class<OrderPaidEvent> getEventClass() {
            return OrderPaidEvent.class;
        }

        @Override
        public void handle(OrderPaidEvent event) {
            log.info("[OrderEventListener] 订单已支付: orderId={}", event.source());
        }
    }

    /**
     * OrderShipped 事件处理器
     */
    class OrderShippedHandler implements TypedEventHandler<OrderShippedEvent> {
        @Override
        public String getEventType() {
            return "OrderShipped";
        }

        @Override
        public Class<OrderShippedEvent> getEventClass() {
            return OrderShippedEvent.class;
        }

        @Override
        public void handle(OrderShippedEvent event) {
            log.info("[OrderEventListener] 订单已发货: orderId={}", event.source());
        }
    }

    /**
     * OrderCancelled 事件处理器
     */
    class OrderCancelledHandler implements TypedEventHandler<OrderCancelledEvent> {
        @Override
        public String getEventType() {
            return "OrderCancelled";
        }

        @Override
        public Class<OrderCancelledEvent> getEventClass() {
            return OrderCancelledEvent.class;
        }

        @Override
        public void handle(OrderCancelledEvent event) {
            log.info("[OrderEventListener] 订单已取消: orderId={}", event.source());
        }
    }
}