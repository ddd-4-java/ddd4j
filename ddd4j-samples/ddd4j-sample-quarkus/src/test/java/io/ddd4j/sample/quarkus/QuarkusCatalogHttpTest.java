package io.ddd4j.sample.quarkus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import io.ddd4j.sample.quarkus.spi.SampleSubjectProvider;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>Contexts.get()</code>、<code>HttpRequest.newBuilder()</code>、<code>base.resolve()</code> 验证 <code>QuarkusCatalogHttp</code> 的运行行为。
 */
@QuarkusTest
@TestProfile(QuarkusCatalogHttpTest.BearerProfile.class)
@QuarkusTestResource(value = CatalogDatabaseResource.class, restrictToAnnotatedClass = true)
class QuarkusCatalogHttpTest {
    /**
     * 发送合同测试请求的 HTTP 客户端。
     */
    private final HttpClient http = HttpClient.newHttpClient();
    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper json = new ObjectMapper();
    /**
     * 业务数据或认证请求所属的测试租户。
     */
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);
    /**
     * 本地测试服务的基础地址。
     */
    @TestHTTPResource
    URI base;
    /**
     * 记录样例订单是否已经确认付款。
     */
    @Inject
    PaidEvents paid;
    /**
     * 当前用例使用的认证主体。
     */
    @Inject
    SampleSubjectProvider subject;
    /**
     * 将领域事件交给运行时分发的发布器。
     */
    @Inject
    DomainEventPublisher publisher;
    /**
     * 提供固定消息或区域的测试国际化组件。
     */
    @Inject
    I18nProvider i18n;

    /**
     * 验证<code>Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class).orElseThrow()</code> 等于 <code>publisher</code>，<code>Contexts.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class).orElseThrow()</code> 等于 <code>subject</code>，<code>Contexts.get(SpiKeys.I18N_PROVIDER, I18nProvider.class).orElseThrow()</code> 等于 <code>i18n</code>。
     */
    @Test
    void runtimeRegistersCoreSpis() {
        assertEquals(publisher, Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class).orElseThrow());
        assertEquals(subject, Contexts.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class).orElseThrow());
        assertEquals(i18n, Contexts.get(SpiKeys.I18N_PROVIDER, I18nProvider.class).orElseThrow());
    }

    /**
     * 验证<code>http.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()</code> 等于 <code>401</code>，<code>paid.snapshot().size()</code> 等于 <code>1</code>，<code>order.path("paidAmountCent").asLong()</code> 等于 <code>2500</code>。
     *
     * @throws Exception 处理测试数据的序列化、发起测试请求时失败
     */
    @Test
    void nativeBearerAndCdiEventProtectTheFourTableTransaction() throws Exception {
        var anonymous = HttpRequest.newBuilder(base.resolve("/api/orders/T001"))
                .header("X-Tenant-Id", tenant).GET().build();
        assertEquals(401, http.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode());
        request("POST", "/goods/LUNCH", 201, """
                {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                """);
        request("POST", "/orders/T001", 201, """
                {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                """);
        request("GET", "/orders/missing", 404, "");
        request("POST", "/orders/T001/payment", 400, "{}");
        request("POST", "/orders/T001/payment", 409, "{\"paidAmountCent\":100}");
        request("POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
        request("POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
        assertEquals(1, paid.snapshot().size());
        assertEquals(tenant, paid.snapshot().get(0).getTenantId());
        var order = json.readTree(request("GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
    }

    /**
     * 通过 <code>response.body()</code> 取得当前测试需要的结果。
     *
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param status 当前 HTTP 或业务流程的状态
     * @param body   当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>response.body()</code>
     * @throws Exception 发起测试请求时失败
     */
    private String request(String method, String path, int status, String body) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve("/api" + path))
                .header("X-Tenant-Id", tenant).header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "").header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + subject.token())
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }

    /**
     * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
     */
    public static final class BearerProfile implements QuarkusTestProfile {
        /**
         * 通过 <code>Map.of()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Map.of("ddd4j.quarkus.web.bearer-required", "true")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("ddd4j.quarkus.web.bearer-required", "true");
        }
    }

    /**
     * 向测试容器注册 <code>snapshot()</code>、<code>onPaid()</code> 等测试入口或组件。
     */
    @ApplicationScoped
    public static class PaidEvents {
        /**
         * 当前测试期间收集的领域事件，供断言比较发布内容或顺序。
         */
        final CopyOnWriteArrayList<TradeOrderPaymentConfirmed> events = new CopyOnWriteArrayList<>();

        /**
         * 通过 <code>java.util.List.copyOf()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>java.util.List.copyOf(events)</code>
         */
        public java.util.List<TradeOrderPaymentConfirmed> snapshot() {
            return java.util.List.copyOf(events);
        }

        /**
         * 调用 <code>events.add()</code> 执行当前夹具操作。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        void onPaid(@Observes TradeOrderPaymentConfirmed event) {
            events.add(event);
        }
    }
}
