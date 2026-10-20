package io.ddd4j.sample.quarkus.cqrs.order.web;

import io.ddd4j.core.api.R;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CQRS 读侧响应字段顺序契约。 */
class OrderCQRSQueryResponseContractTest {

    @Test
    void listKeepsDocumentedFieldOrder() {
        Response response = new OrderCQRSQueryResource(null).list(null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ((R<?>) response.getEntity()).getData();
        assertEquals(Arrays.asList("page", "pageSize", "note"), new ArrayList<>(data.keySet()));
        assertEquals(1, data.get("page"));
        assertEquals(10, data.get("pageSize"));
    }
}
