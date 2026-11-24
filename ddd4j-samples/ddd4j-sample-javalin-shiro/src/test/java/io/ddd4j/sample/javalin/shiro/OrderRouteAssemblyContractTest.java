package io.ddd4j.sample.javalin.shiro;

import io.ddd4j.sample.javalin.shiro.order.domain.model.Money;
import io.ddd4j.sample.javalin.shiro.order.domain.service.OrderDomainService;
import io.ddd4j.sample.javalin.shiro.order.infrastructure.InMemoryOrderRepository;
import io.ddd4j.sample.javalin.shiro.order.web.OrderResource;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.sample.order.testkit.EnvelopeOrderHttpDriver;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.apibuilder.ApiBuilder;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 通过实际原生认证应用验证公共路由装配和传入折扣规则。 */
class OrderRouteAssemblyContractTest {
    @Test
    void twoArgumentConstructorUsesDefaultDiscountOnAuthorizedHttpRoute() {
        InMemoryOrderAdapters store = new InMemoryOrderAdapters();
        OrderApplicationService shared = new OrderApplicationService(store, store, store, store);
        verify(new OrderResource(shared, store).routes(), "1800", null);
    }

    @Test
    void suppliedDiscountPolicyIsUsedForEveryAuthorizedPreview() {
        InMemoryOrderAdapters store = new InMemoryOrderAdapters();
        OrderApplicationService shared = new OrderApplicationService(store, store, store, store);
        AtomicInteger calls = new AtomicInteger();
        OrderDomainService policy = new OrderDomainService(new InMemoryOrderRepository()) {
            @Override
            public Money calculateDiscount(String buyerId, Money total) {
                assertEquals("route-buyer", buyerId);
                assertEquals(0, new BigDecimal("2000").compareTo(total.amount()));
                calls.incrementAndGet();
                return total.discount(25);
            }
        };
        verify(new OrderResource(shared, store, policy).routes(), "1500", calls);
    }

    private void verify(EndpointGroup routes, String expected, AtomicInteger calls) {
        try (JavalinShiroApplication.Application application = JavalinShiroApplication.start(0, ignored -> { }, config -> {
            config.jetty.host = "127.0.0.1";
            config.routes.apiBuilder(() -> ApiBuilder.path("/orders/custom", routes));
        })) {
            String base = "http://127.0.0.1:" + application.app().port();
            EnvelopeOrderHttpDriver anonymous = new EnvelopeOrderHttpDriver(base, "/orders/custom/orders", Collections.emptyMap());
            Map<String, String> login = new HashMap<>();
            login.put("loginId", "zhangsan");
            login.put("password", "pass123");
            String token = anonymous.request("POST", "/auth/login", login, Collections.emptyMap()).path("token").asText();
            assertFalse(token.isEmpty());
            Map<String, String> headers = Collections.singletonMap("ddd4j-token", token);
            EnvelopeOrderHttpDriver authorized = new EnvelopeOrderHttpDriver(base, "/orders/custom/orders", headers);
            Map<String, Object> create = new HashMap<>();
            create.put("orderNo", "ASSEMBLY");
            create.put("buyerId", "route-buyer");
            create.put("buyerName", "Route buyer");
            String id = authorized.request("POST", "/orders/custom/orders", create, Collections.emptyMap()).path("id").asText();
            assertFalse(id.isEmpty());
            Map<String, Object> line = new HashMap<>();
            line.put("goodsId", "goods");
            line.put("goodsName", "Goods");
            line.put("quantity", 2);
            line.put("unitPrice", new BigDecimal("1000"));
            authorized.request("POST", "/orders/custom/orders/" + id + "/lines", line, Collections.emptyMap());
            String discountPath = "/orders/custom/orders/" + id + "/discount";
            SecurityException denied = assertThrows(SecurityException.class,
                    () -> anonymous.request("GET", discountPath, null, Collections.emptyMap()));
            assertEquals("HTTP 401", denied.getMessage());
            for (int request = 0; request < 2; request++) {
                assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(authorized.request(
                        "GET", discountPath, null, Collections.emptyMap()).path("amount").asText())));
                assertEquals("CNY", authorized.request("GET", discountPath, null, Collections.emptyMap()).path("currency").asText());
            }
            if (Objects.nonNull(calls)) {
                assertEquals(4, calls.get(), "every discount HTTP request must use the supplied instance");
            }
        }
    }
}
