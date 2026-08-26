package io.ddd4j.sample.dropwizard;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.dropwizard.DropwizardDomainEventPublisher;
import io.ddd4j.dropwizard.Ddd4jDropwizardRuntime;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardExceptionMapper;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardIllegalStateExceptionMapper;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardRequestFilter;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardResponseFilter;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(DropwizardExtensionsSupport.class)
class DropwizardOrderResourceTest {

    private static final String IDEMPOTENCY_CACHE_NAME = "ddd4j-web-idempotency";
    private static final long IDEMPOTENCY_CACHE_TTL_SECONDS = 300L;

    private static final InMemorySubjectProvider SUBJECT_PROVIDER = new InMemorySubjectProvider(
            new InMemorySubject(event -> {
            }));
    private static final Ddd4jDropwizardRuntime RUNTIME = new Ddd4jDropwizardRuntime(
            new DropwizardDomainEventPublisher(List.of()), SUBJECT_PROVIDER, I18nProvider.DEFAULT,
            new DefaultCommandBus(List.of()));
    private static final InMemoryOrderAdapters ADAPTERS = new InMemoryOrderAdapters();
    private static final OrderApplicationService APPLICATION_SERVICE = new OrderApplicationService(ADAPTERS, ADAPTERS,
            ADAPTERS, ADAPTERS, ADAPTERS);
    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new DropwizardOrderResource(APPLICATION_SERVICE))
            .addProvider(new Ddd4jDropwizardRequestFilter(webConfiguration()))
            .addProvider(new Ddd4jDropwizardResponseFilter())
            .addProvider(new Ddd4jDropwizardExceptionMapper())
            .addProvider(new Ddd4jDropwizardIllegalStateExceptionMapper())
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void startRuntime() {
        CacheKit.build(IDEMPOTENCY_CACHE_NAME, IDEMPOTENCY_CACHE_TTL_SECONDS);
        RUNTIME.start();
    }

    @AfterAll
    static void closeRuntime() {
        RUNTIME.close();
        CacheKit.unregister(IDEMPOTENCY_CACHE_NAME);
    }

    @Test
    void shouldRunSharedOrderUseCasesThroughDropwizardHttp() throws Exception {
        String token = issueToken();
        Response create = request("POST", "/api/orders", token,
                "{\"orderNo\":\"DROPWIZARD-001\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}");
        String createBody = readAndClose(create);
        assertThat(create.getStatus()).withFailMessage(createBody).isEqualTo(201);
        String orderId = objectMapper.readTree(createBody).path("data").path("id").asText();

        Response line = request("POST", "/api/orders/" + orderId + "/lines", token,
                "{\"goodsId\":\"goods-1\",\"goodsName\":\"DDD Book\",\"quantity\":2,\"unitPrice\":59.90}");
        String lineBody = readAndClose(line);
        assertThat(line.getStatus()).withFailMessage(lineBody).isEqualTo(200);
        assertThat(objectMapper.readTree(lineBody).path("data").path("totalAmount").decimalValue())
                .isEqualByComparingTo("119.80");

        Response missingKey = request("POST", "/api/orders/" + orderId + "/pay", token, null);
        String missingKeyBody = readAndClose(missingKey);
        assertThat(missingKey.getStatus()).withFailMessage(missingKeyBody).isEqualTo(400);

        Invocation.Builder payment = RESOURCES.target("/api/orders/" + orderId + "/pay").request()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "dropwizard-payment-001");
        Response paid = payment.post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE));
        String paidBody = readAndClose(paid);
        assertThat(paid.getStatus()).withFailMessage(paidBody).isEqualTo(200);
        assertThat(objectMapper.readTree(paidBody).path("data").path("status").asText()).isEqualTo("PAID");
    }

    private static Ddd4jDropwizardWebConfiguration webConfiguration() {
        Ddd4jDropwizardWebConfiguration configuration = new Ddd4jDropwizardWebConfiguration();
        configuration.setPublicPaths(List.of("/health", "/healthcheck/**", "/api/auth/**"));
        return configuration;
    }

    private String issueToken() throws Exception {
        Response response = request("POST", "/api/auth/tokens/dropwizard-user", null, null);
        String body = readAndClose(response);
        assertThat(response.getStatus()).withFailMessage(body).isEqualTo(200);
        JsonNode payload = objectMapper.readTree(body);
        String token = payload.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private Response request(String method, String path, String token, String body) {
        Invocation.Builder builder = RESOURCES.target(path).request();
        if (Objects.nonNull(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
        return Objects.isNull(body)
                ? builder.method(method)
                : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }

    private String readAndClose(Response response) {
        try (response) {
            return response.readEntity(String.class);
        }
    }
}
