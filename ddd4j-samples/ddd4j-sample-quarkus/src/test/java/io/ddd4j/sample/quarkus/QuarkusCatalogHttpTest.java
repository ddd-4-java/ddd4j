package io.ddd4j.sample.quarkus;

import java.util.NoSuchElementException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>Contexts.get()</code>\u3001<code>HttpRequest.newBuilder()</code>\u3001<code>base.resolve()</code> \u9a8c\u8bc1 <code>QuarkusCatalogHttp</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@QuarkusTest
@TestProfile(QuarkusCatalogHttpTest.BearerProfile.class)
@QuarkusTestResource(value = CatalogDatabaseResource.class)
class QuarkusCatalogHttpTest {
    /** \u53d1\u9001\u5408\u540c\u6d4b\u8bd5\u8bf7\u6c42\u7684 HTTP \u5ba2\u6237\u7aef\u3002 */
    private final HttpClient http = HttpClient.newHttpClient();
    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper json = new ObjectMapper();
    /** \u4e1a\u52a1\u6570\u636e\u6216\u8ba4\u8bc1\u8bf7\u6c42\u6240\u5c5e\u7684\u6d4b\u8bd5\u79df\u6237\u3002 */
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);
    /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
    @TestHTTPResource
    URI base;
    /** \u8bb0\u5f55\u6837\u4f8b\u8ba2\u5355\u662f\u5426\u5df2\u7ecf\u786e\u8ba4\u4ed8\u6b3e\u3002 */
    @Inject
    PaidEvents paid;
    /** \u5f53\u524d\u7528\u4f8b\u4f7f\u7528\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002 */
    @Inject
    SampleSubjectProvider subject;
    /** \u5c06\u9886\u57df\u4e8b\u4ef6\u4ea4\u7ed9\u8fd0\u884c\u65f6\u5206\u53d1\u7684\u53d1\u5e03\u5668\u3002 */
    @Inject
    DomainEventPublisher publisher;
    /** \u63d0\u4f9b\u56fa\u5b9a\u6d88\u606f\u6216\u533a\u57df\u7684\u6d4b\u8bd5\u56fd\u9645\u5316\u7ec4\u4ef6\u3002 */
    @Inject
    I18nProvider i18n;

    /** \u9a8c\u8bc1<code>Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class).orElseThrow()</code> \u7b49\u4e8e <code>publisher</code>\uff0c<code>Contexts.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class).orElseThrow()</code> \u7b49\u4e8e <code>subject</code>\uff0c<code>Contexts.get(SpiKeys.I18N_PROVIDER, I18nProvider.class).orElseThrow()</code> \u7b49\u4e8e <code>i18n</code>\u3002 */
    @Test
    void runtimeRegistersCoreSpis() {
        assertEquals(publisher, Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class).orElseThrow(NoSuchElementException::new));
        assertEquals(subject, Contexts.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class).orElseThrow(NoSuchElementException::new));
        assertEquals(i18n, Contexts.get(SpiKeys.I18N_PROVIDER, I18nProvider.class).orElseThrow(NoSuchElementException::new));
    }

    /**
     * \u9a8c\u8bc1<code>http.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()</code> \u7b49\u4e8e <code>401</code>\uff0c<code>paid.snapshot().size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>order.path("paidAmountCent").asLong()</code> \u7b49\u4e8e <code>2500</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void nativeBearerAndCdiEventProtectTheFourTableTransaction() throws Exception {
        HttpRequest anonymous = HttpRequest.newBuilder(base.resolve("/api/orders/T001"))
                .header("X-Tenant-Id", tenant).GET().build();
        assertEquals(401, http.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode());
        request("POST", "/goods/LUNCH", 201, "{\"spuName\":\"Lunch\",\"skuCode\":\"S1\",\"skuName\":\"Weekday\",\"salePriceCent\":1250,\"shopId\":7}\n");
        request("POST", "/orders/T001", 201, "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n");
        request("GET", "/orders/missing", 404, "");
        request("POST", "/orders/T001/payment", 400, "{}");
        request("POST", "/orders/T001/payment", 409, "{\"paidAmountCent\":100}");
        request("POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
        request("POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
        assertEquals(1, paid.snapshot().size());
        assertEquals(tenant, paid.snapshot().get(0).getTenantId());
        com.fasterxml.jackson.databind.JsonNode order = json.readTree(request("GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
    }

    /**
     * \u901a\u8fc7 <code>response.body()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>response.body()</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private String request(String method, String path, int status, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api" + path))
                .header("X-Tenant-Id", tenant).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + subject.token())
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.lang.String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }

    /** \u5b9e\u73b0 <code>QuarkusTestProfile</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConfigOverrides()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static final class BearerProfile implements QuarkusTestProfile {
        /**
         * \u901a\u8fc7 <code>Map.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Map.of("ddd4j.quarkus.web.bearer-required", "true")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("ddd4j.quarkus.web.bearer-required", "true");
        }
    }

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>snapshot()</code>\u3001<code>onPaid()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @ApplicationScoped
    public static class PaidEvents {
        /** \u5f53\u524d\u6d4b\u8bd5\u671f\u95f4\u6536\u96c6\u7684\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u6bd4\u8f83\u53d1\u5e03\u5185\u5bb9\u6216\u987a\u5e8f\u3002 */
        final CopyOnWriteArrayList<TradeOrderPaymentConfirmed> events = new CopyOnWriteArrayList<>();

        /**
         * \u901a\u8fc7 <code>java.util.List.copyOf()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>java.util.List.copyOf(events)</code>
         */
        public java.util.List<TradeOrderPaymentConfirmed> snapshot() {
            return ImmutableList.copyOf(events);
        }

        /**
         * \u8c03\u7528 <code>events.add()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        void onPaid(@Observes TradeOrderPaymentConfirmed event) {
            events.add(event);
        }
    }
}
