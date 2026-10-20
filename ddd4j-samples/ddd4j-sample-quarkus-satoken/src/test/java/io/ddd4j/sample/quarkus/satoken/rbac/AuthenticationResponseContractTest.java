package io.ddd4j.sample.quarkus.satoken.rbac;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 认证业务端点响应的三线行为契约。 */
class AuthenticationResponseContractTest {

    @Test
    void paymentResponseKeepsOrderAndNullableId() {
        Map<String, Object> data = new AuthenticationResource().businessPayOrder(null).getData();
        assertEquals(Arrays.asList("orderId", "paid"), new ArrayList<>(data.keySet()));
        assertNull(data.get("orderId"));
        assertEquals(true, data.get("paid"));
    }
}
