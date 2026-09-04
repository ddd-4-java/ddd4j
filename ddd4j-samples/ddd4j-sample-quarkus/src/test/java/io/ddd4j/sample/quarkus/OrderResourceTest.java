package io.ddd4j.sample.quarkus;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.quarkus.order.infrastructure.QuarkusOrderAdapters;
import io.ddd4j.sample.quarkus.spi.SampleSubjectProvider;
import io.ddd4j.web.core.context.WebHeaders;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/** Shared Order kernel and HTTP contract verification for Quarkus. */
@QuarkusTest
class OrderResourceTest {

    @Inject
    OrderApplicationService applicationService;

    @Inject
    QuarkusOrderAdapters adapters;

    @Inject
    SampleSubjectProvider subjectProvider;

    @Inject
    DomainEventPublisher domainEventPublisher;

    @Inject
    I18nProvider i18nProvider;

    @Test
    void runtimeRegistersCoreSpis() {
        assertThat(Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class))
                .contains(domainEventPublisher);
        assertThat(Contexts.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class))
                .contains(subjectProvider);
        assertThat(Contexts.get(SpiKeys.I18N_PROVIDER, I18nProvider.class))
                .contains(i18nProvider);
    }

    @Test
    void sharedApplicationPersistsProjectionOutboxAndIdempotency() {
        Order order = applicationService.create(new CreateOrderCommand(
                "Q-SHARED-1", "buyer-1", "Alice"));
        applicationService.addLine(new AddOrderLineCommand(order.id(), "sku-1", "Book",
                2, new BigDecimal("19.90")));

        Order paid = applicationService.pay(order.id(), "payment-" + order.id());
        Order repeated = applicationService.pay(order.id(), "payment-" + order.id());

        assertThat(paid.status().name()).isEqualTo("PAID");
        assertThat(repeated.status().name()).isEqualTo("PAID");
        assertThat(applicationService.find(order.id()).totalAmount()).isEqualByComparingTo("39.80");
        assertThat(adapters.pending(20))
                .filteredOn(message -> message.aggregateId().equals(order.id()))
                .hasSize(3);
    }

    @Test
    void protectedRouteRequiresBearer() {
        given().when().get("/api/orders").then().statusCode(401)
                .body("code", equalTo(401));
    }

    @Test
    void createQueryAndLookupUseSharedKernel() {
        String orderId = authorized().contentType(ContentType.JSON)
                .body("{\"orderNo\":\"Q-HTTP-1\",\"buyerId\":\"buyer-http\",\"buyerName\":\"Bob\"}")
                .when().post("/api/orders")
                .then().statusCode(201)
                .header(WebHeaders.REQUEST_ID, notNullValue())
                .body("code", equalTo(0))
                .body("data.orderNo", equalTo("Q-HTTP-1"))
                .extract().path("data.id");

        authorized().when().get("/api/orders/{id}", orderId)
                .then().statusCode(200).body("data.id", equalTo(orderId));
        authorized().queryParam("orderNo", "Q-HTTP-1").when().get("/api/orders/by-no")
                .then().statusCode(200).body("data.orderNo", equalTo("Q-HTTP-1"));
        authorized().queryParam("buyerId", "buyer-http").when().get("/api/orders")
                .then().statusCode(200).body("data[0].id", equalTo(orderId));
    }

    @Test
    void missingOrderUsesStandard404() {
        authorized().when().get("/api/orders/missing")
                .then().statusCode(404).body("code", equalTo(404));
    }

    @Test
    void paymentRequiresIdempotencyKeyAndRejectsInvalidState() {
        String orderId = authorized().contentType(ContentType.JSON)
                .body("{\"orderNo\":\"Q-HTTP-2\",\"buyerId\":\"buyer-2\",\"buyerName\":\"Carol\"}")
                .when().post("/api/orders").then().statusCode(201).extract().path("data.id");

        authorized().when().post("/api/orders/{id}/pay", orderId)
                .then().statusCode(400).body("code", equalTo(400));
        authorized().header(WebHeaders.IDEMPOTENCY_KEY, "empty-" + orderId)
                .when().post("/api/orders/{id}/pay", orderId)
                .then().statusCode(409).body("code", equalTo(409));
    }

    private io.restassured.specification.RequestSpecification authorized() {
        return given().header(WebHeaders.AUTHORIZATION, "Bearer " + subjectProvider.token());
    }
}
