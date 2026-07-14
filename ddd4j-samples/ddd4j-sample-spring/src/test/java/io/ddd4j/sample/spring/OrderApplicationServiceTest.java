package io.ddd4j.sample.spring;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.sample.spring.order.application.AddOrderLineCommand;
import io.ddd4j.sample.spring.order.application.CreateOrderCommand;
import io.ddd4j.sample.spring.order.application.OrderApplicationService;
import io.ddd4j.sample.spring.order.application.OrderQueryService;
import io.ddd4j.sample.spring.order.domain.model.Money;
import io.ddd4j.sample.spring.order.domain.model.Order;
import io.ddd4j.sample.spring.order.domain.model.OrderStatus;
import io.ddd4j.sample.spring.order.domain.repository.OrderRepository;
import io.ddd4j.sample.spring.order.domain.service.OrderDomainService;
import io.ddd4j.sample.spring.order.infrastructure.InMemoryOrderRepository;
import org.fuin.ddd4j.core.EntityId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单应用服务测试（无需启动 Spring 容器）。
 *
 * <p>本测试演示：在不依赖 Spring 容器的前提下，
 * 仍然可以通过手工装配完整测试 {@link OrderApplicationService} 的业务逻辑。
 * 这正是 ddd4j 框架"业务零框架耦合"的价值体现。
 *
 * <p>由于应用服务在用例编排中会调用 {@code DomainEvent.publish()}，
 * 测试环境通过 {@link BaseContext#inject} 注册一个 no-op 的 {@link DomainEventPublisher}，
 * 模拟 ddd4j-runtime-spring 在真实启动时的 SPI 注入行为。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class OrderApplicationServiceTest {

    private OrderRepository orderRepository;
    private OrderDomainService orderDomainService;
    private OrderApplicationService orderApplicationService;
    private OrderQueryService orderQueryService;

    @BeforeEach
    void setUp() {
        // 注册一个 no-op DomainEventPublisher 到 ddd4j SPI，模拟 Spring 启动时的注入
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, new NoOpDomainEventPublisher());
        orderRepository = new InMemoryOrderRepository();
        orderDomainService = new OrderDomainService(orderRepository);
        orderApplicationService = new OrderApplicationService(orderRepository, orderDomainService);
        orderQueryService = new OrderQueryService(orderRepository);
    }

    @AfterEach
    void tearDown() {
        // 清理 SPI 注册，避免用例间相互影响
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
    }

    @Test
    void shouldCreateDraftAndAddLineAndPay() {
        // 创建草稿订单
        Order draft = orderApplicationService.createDraft(
                new CreateOrderCommand("ORDER-1001", "BUYER-1", "Alice")
        );

        assertThat(draft.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(draft.orderNo()).isEqualTo("ORDER-1001");
        assertThat(draft.buyerName()).isEqualTo("Alice");
        assertThat(draft.domainEvents()).isEmpty(); // 事件已被清空
        assertThat(orderRepository.findByOrderNo("ORDER-1001")).isPresent();

        // 添加订单行
        Order withLine = orderApplicationService.addLine(new AddOrderLineCommand(
                draft.id(),
                "SKU-1",
                "DDD Book",
                2,
                new BigDecimal("39.80")
        ));

        assertThat(withLine.lines()).hasSize(1);
        assertThat(withLine.lines().get(0).goodsName()).isEqualTo("DDD Book");

        // 支付订单
        Order paid = orderApplicationService.pay(withLine.id());

        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.totalAmount().amount()).isEqualByComparingTo("79.60");
    }

    @Test
    void shouldThrowWhenPayingEmptyOrder() {
        Order draft = orderApplicationService.createDraft(
                new CreateOrderCommand("ORDER-1002", "BUYER-1", "Alice")
        );

        assertThatThrownBy(() -> orderApplicationService.pay(draft.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("order line must not be empty");
    }

    @Test
    void shouldShipOnlyPaidOrder() {
        Order draft = orderApplicationService.createDraft(
                new CreateOrderCommand("ORDER-1003", "BUYER-2", "Bob")
        );
        orderApplicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU-1", "Item", 1, new BigDecimal("10.00")
        ));

        // 未支付时不能发货
        assertThatThrownBy(() -> orderApplicationService.ship(draft.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only paid order can be shipped");

        // 支付后可以发货
        orderApplicationService.pay(draft.id());
        Order shipped = orderApplicationService.ship(draft.id());
        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shouldNotCancelShippedOrder() {
        Order draft = orderApplicationService.createDraft(
                new CreateOrderCommand("ORDER-1004", "BUYER-3", "Carol")
        );
        orderApplicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU-1", "Item", 1, new BigDecimal("10.00")
        ));
        orderApplicationService.pay(draft.id());
        orderApplicationService.ship(draft.id());

        // 已发货不可取消
        assertThatThrownBy(() -> orderApplicationService.cancel(draft.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shipped order cannot be cancelled");
    }

    @Test
    void shouldCancelDraftOrder() {
        Order draft = orderApplicationService.createDraft(
                new CreateOrderCommand("ORDER-1006", "BUYER-6", "Frank")
        );
        Order cancelled = orderApplicationService.cancel(draft.id());
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldCalculateDiscountForHighAmountOrder() {
        Order draft = orderApplicationService.createDraft(
                new CreateOrderCommand("ORDER-1005", "BUYER-4", "Dave")
        );
        // 添加 600 元的订单行（≥500，95折）
        orderApplicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU-EXP", "Expensive Item", 1, new BigDecimal("600.00")
        ));

        Money discounted = orderApplicationService.previewDiscount(draft.id());
        assertThat(discounted.amount()).isEqualByComparingTo("570.00"); // 600 * 0.95
    }

    @Test
    void shouldFindByOrderNo() {
        orderApplicationService.createDraft(new CreateOrderCommand("ORDER-FIND", "BUYER-5", "Eve"));
        Order found = orderApplicationService.findByOrderNo("ORDER-FIND");
        assertThat(found.buyerName()).isEqualTo("Eve");
    }

    @Test
    void shouldQueryOrdersWithPagination() {
        // 创建多个订单
        for (int i = 1; i <= 5; i++) {
            orderApplicationService.createDraft(
                    new CreateOrderCommand("ORDER-PAGE-" + i, "BUYER-P", "Pager")
            );
        }

        // 查询第 1 页，每页 2 条
        OrderQueryService.PageResult page1 = orderQueryService.listOrders(1, 2);
        assertThat(page1.items()).hasSize(2);
        assertThat(page1.total()).isEqualTo(5);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.pageSize()).isEqualTo(2);

        // 查询第 3 页，每页 2 条（最后一页只有 1 条）
        OrderQueryService.PageResult page3 = orderQueryService.listOrders(3, 2);
        assertThat(page3.items()).hasSize(1);
    }

    /**
     * 测试用 No-Op 领域事件发布器。
     */
    static class NoOpDomainEventPublisher implements DomainEventPublisher {

        @Override
        public <ID extends EntityId> void publish(DomainEvent<ID> event) {
            // no-op for unit tests
        }

        @Override
        public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> events) {
            if (Objects.nonNull(events)) {
                events.forEach(this::publish);
            }
        }
    }
}
