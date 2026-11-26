package io.ddd4j.sample.quarkus.cqrs;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.common.collect.ImmutableList;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.quarkus.command.QuarkusCommandBus;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(value = CatalogDatabaseResource.class)
class QuarkusCatalogCqrsHttpTest {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);
    @TestHTTPResource
    URI base;
    @Inject
    CommandBus bus;
    @Inject
    PaidEvents paid;

    @Test
    void nativeCommandBusAndCdiEventJoinFourTableTransactionAndSnapshotView() throws Exception {
        assertTrue(bus instanceof QuarkusCommandBus);
        request("POST", "/goods/LUNCH", 201, "{\"spuName\":\"Lunch\",\"skuCode\":\"S1\",\"skuName\":\"Weekday\",\"salePriceCent\":1250,\"shopId\":7}\n");
        request("POST", "/orders/T001", 201, "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n");
        request("GET", "/orders/T001", 404, "");
        request("POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
        request("POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
        assertEquals(1, paid.snapshot().size());
        assertEquals(tenant, paid.snapshot().get(0).getTenantId());
        request("POST", "/projections/orders/T001", 200, "");
        com.fasterxml.jackson.databind.JsonNode order = json.readTree(request("GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
    }

    private String request(String method, String path, int status, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/orders/catalog" + path))
                .header("X-Tenant-Id", tenant).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.lang.String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
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
