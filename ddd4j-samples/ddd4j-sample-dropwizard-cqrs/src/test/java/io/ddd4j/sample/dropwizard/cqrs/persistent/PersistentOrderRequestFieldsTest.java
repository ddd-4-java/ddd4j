package io.ddd4j.sample.dropwizard.cqrs.persistent;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentOrderRequestFieldsTest {
    private static final String ORDER_ID = "resource-contract-id";
    private static final String UNKNOWN_FIELD = "unexpectedField";

    private final CapturingCommandBus commands = new CapturingCommandBus();
    private final PersistentOrderResource resource = new PersistentOrderResource(commands, null, () -> {
        throw new AssertionError("Order writes must not invoke projection");
    });

    @Test
    void createDispatchesConvertedCommandAndReturnsCreatedOrderId() {
        try (Response response = resource.create(createBody())) {
            assertEquals(201, response.getStatus());
            assertEquals(Collections.singletonMap("orderId", ORDER_ID), response.getEntity());
        }
        assertEquals(1, commands.count);
        assertEquals(new CreateOrderCommand("ORDER-1", "buyer-1", "Alice"), commands.last.create);
        assertNull(commands.last.line);
    }

    @Test
    void lineDispatchesConvertedCommandUsingPathOrderId() {
        Map<String, Object> body = lineBody();
        body.put("orderId", "ignored-body-order-id");

        assertEquals(Collections.singletonMap("orderId", ORDER_ID), resource.line(ORDER_ID, body));
        assertEquals(1, commands.count);
        assertEquals(new AddOrderLineCommand(ORDER_ID, "sku-1", "Book", 2, new BigDecimal("19.95")),
                commands.last.line);
        assertNull(commands.last.create);
    }

    @Test
    void createRejectsUnknownFieldBeforeDispatch() {
        Map<String, Object> body = createBody();
        body.put(UNKNOWN_FIELD, true);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resource.create(body),
                () -> "create produced no rejection; dispatch count=" + commands.count
                        + ", converted create DTO=" + (Objects.isNull(commands.last) ? null : commands.last.create));
        assertTrue(error.getMessage().contains(UNKNOWN_FIELD));
        assertEquals(0, commands.count);
    }

    @Test
    void lineRejectsUnknownFieldBeforeDispatch() {
        Map<String, Object> body = lineBody();
        body.put(UNKNOWN_FIELD, true);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resource.line(ORDER_ID, body),
                () -> "line produced no rejection; dispatch count=" + commands.count
                        + ", converted line DTO=" + (Objects.isNull(commands.last) ? null : commands.last.line));
        assertTrue(error.getMessage().contains(UNKNOWN_FIELD));
        assertEquals(0, commands.count);
    }

    private static Map<String, Object> createBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNo", "ORDER-1");
        body.put("buyerId", "buyer-1");
        body.put("buyerName", "Alice");
        return body;
    }

    private static Map<String, Object> lineBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("goodsId", "sku-1");
        body.put("goodsName", "Book");
        body.put("quantity", 2);
        body.put("unitPrice", "19.95");
        return body;
    }

    private static final class CapturingCommandBus implements CommandBus {
        private OrderWriteCommand last;
        private int count;

        @Override
        @SuppressWarnings("unchecked")
        public <R> Result<R> execute(Command command) {
            last = (OrderWriteCommand) command;
            count++;
            return (Result<R>) Result.ok(ORDER_ID);
        }
    }
}
