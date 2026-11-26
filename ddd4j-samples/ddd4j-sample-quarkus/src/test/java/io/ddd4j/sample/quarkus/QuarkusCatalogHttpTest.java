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

@QuarkusTest
@TestProfile(QuarkusCatalogHttpTest.BearerProfile.class)
@QuarkusTestResource(value = CatalogDatabaseResource.class)
class QuarkusCatalogHttpTest {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);
    @TestHTTPResource
    URI base;
    @Inject
    PaidEvents paid;
    @Inject
    SampleSubjectProvider subject;
    @Inject
    DomainEventPublisher publisher;
    @Inject
    I18nProvider i18n;

    @Test
    void runtimeRegistersCoreSpis() {
        assertEquals(publisher, Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class).orElseThrow(NoSuchElementException::new));
        assertEquals(subject, Contexts.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class).orElseThrow(NoSuchElementException::new));
        assertEquals(i18n, Contexts.get(SpiKeys.I18N_PROVIDER, I18nProvider.class).orElseThrow(NoSuchElementException::new));
    }

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

    private String request(String method, String path, int status, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api" + path))
                .header("X-Tenant-Id", tenant).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + subject.token())
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.lang.String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }

    public static final class BearerProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("ddd4j.quarkus.web.bearer-required", "true");
        }
    }

    @ApplicationScoped
    public static class PaidEvents {
        final CopyOnWriteArrayList<TradeOrderPaymentConfirmed> events = new CopyOnWriteArrayList<>();

        public java.util.List<TradeOrderPaymentConfirmed> snapshot() {
            return ImmutableList.copyOf(events);
        }

        void onPaid(@Observes TradeOrderPaymentConfirmed event) {
            events.add(event);
        }
    }
}
