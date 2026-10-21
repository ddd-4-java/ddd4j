package io.ddd4j.sample.quarkus.cqrs.order.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单行输入与只读快照的三线共同契约。
 */
class OrderLinesContractTest {

    @Test
    void constructorRejectsNullOrderLine() {
        assertThatThrownBy(() -> new Order("order-1", "NO-1", "buyer-1", "Buyer",
                OrderStatus.DRAFT, Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("order line");
    }

    @Test
    void linesReturnsDefensiveUnmodifiableSnapshot() {
        List<OrderLine> input = new ArrayList<>();
        Order order = new Order("order-1", "NO-1", "buyer-1", "Buyer", OrderStatus.DRAFT, input);
        input.add(new OrderLine("goods-1", "Goods", 1, Money.zero("CNY")));

        assertThat(order.lines()).isEmpty();
        assertThatThrownBy(() -> order.lines().add(input.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
