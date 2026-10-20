package io.ddd4j.sample.quarkus.cqrs.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.quarkus.cqrs.order.infrastructure.InMemoryOrderRepository;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 基础查询端列表结果的三线行为契约。 */
class OrderQueryListContractTest {

    @Test
    void inMemoryResultRemainsMutable() {
        Response response = new OrderQueryResource(null, new InMemoryOrderRepository()).list(null);
        @SuppressWarnings("unchecked")
        List<Object> data = (List<Object>) ((R<?>) response.getEntity()).getData();
        assertEquals(0, data.size());
        data.add(null);
        assertEquals(1, data.size());
    }
}
