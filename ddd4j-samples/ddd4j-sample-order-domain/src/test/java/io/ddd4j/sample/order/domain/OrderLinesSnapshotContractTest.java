package io.ddd4j.sample.order.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLinesSnapshotContractTest {

    @Test
    void reconstructedLinesReturnAnUnmodifiableSnapshotIncludingLegacyNullSlots() {
        List<OrderLine> restoredLines = new ArrayList<>();
        restoredLines.add(null);
        Order order = new Order("order-1", "ORD-1", "buyer-1", "Alice",
                OrderStatus.DRAFT, restoredLines);

        List<OrderLine> snapshot = order.lines();
        restoredLines.clear();

        assertEquals(1, snapshot.size());
        assertNull(snapshot.get(0));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
    }
}
