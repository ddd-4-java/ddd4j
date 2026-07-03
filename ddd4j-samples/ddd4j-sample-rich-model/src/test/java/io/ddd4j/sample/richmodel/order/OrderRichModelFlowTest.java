package io.ddd4j.sample.richmodel.order;

import io.ddd4j.sample.richmodel.order.application.AddOrderLineCommand;
import io.ddd4j.sample.richmodel.order.application.CreateOrderCommand;
import io.ddd4j.sample.richmodel.order.application.OrderApplicationService;
import io.ddd4j.sample.richmodel.order.domain.event.OrderPaidEvent;
import io.ddd4j.sample.richmodel.order.domain.model.Order;
import io.ddd4j.sample.richmodel.order.domain.model.OrderStatus;
import io.ddd4j.sample.richmodel.order.infrastructure.persistence.InMemoryOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单富模型流程集成测试。
 *
 * <p>验证订单创建、添加商品、支付完整流程，
 * 以及领域模型与持久化对象保持分离。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class OrderRichModelFlowTest {

    /**
     * 验证订单完整流程：创建草稿 → 添加商品 → 支付，
     * 以及领域模型与持久化对象保持分离。
     */
    @Test
    void shouldKeepDomainModelAndPersistenceObjectSeparated() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        OrderApplicationService service = new OrderApplicationService(repository);

        Order draft = service.createDraft(new CreateOrderCommand("ORDER-1001", "BUYER-1", "Alice"));
        Order withLine = service.addLine(new AddOrderLineCommand(
                draft.id(),
                "SKU-1",
                "DDD Book",
                2,
                new BigDecimal("39.80")
        ));
        Order paid = service.pay(withLine.id());

        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.totalAmount().amount()).isEqualByComparingTo("79.60");
        assertThat(paid.domainEvents()).anyMatch(OrderPaidEvent.class::isInstance);
        assertThat(repository.findByOrderNo("ORDER-1001"))
                .hasValueSatisfying(saved -> assertThat(saved.status()).isEqualTo(OrderStatus.PAID));
    }
}
