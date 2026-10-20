package io.ddd4j.sample.javalin.shiro.order.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLinesContractTest {

    @Test
    void linesRejectNullElementsAndRemainImmutable() {
        Order invalid = new Order("order-1", "O-1", "buyer-1", "Alice",
                OrderStatus.DRAFT, Collections.singletonList(null));
        assertThrows(NullPointerException.class, invalid::lines);

        Order draft = Order.draft("O-2", "buyer-1", "Alice");
        assertThrows(UnsupportedOperationException.class, () -> draft.lines().add(null));
    }
}
