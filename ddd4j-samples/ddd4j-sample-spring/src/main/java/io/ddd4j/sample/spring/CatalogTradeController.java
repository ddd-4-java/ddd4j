package io.ddd4j.sample.spring;

import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.spring.event.SpringDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;

@Profile("catalog-trade")
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogTradeController {
    private final CatalogApi api;

    public CatalogTradeController(org.springframework.core.env.Environment environment, ApplicationEventPublisher publisher) {
        api = CatalogRuntime.start(environment.getRequiredProperty("CATALOG_JDBC_URL"),
                environment.getRequiredProperty("CATALOG_DB_USER"), environment.getRequiredProperty("CATALOG_DB_PASSWORD"),
                new SpringDomainEventPublisher(publisher));
    }

    @RawResponse
    @PostMapping("/goods/{code}")
    public Mono<ResponseEntity<JsonNode>> createGoods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd body) {
        return respond(() -> api.createGoods(tenant, code, body));
    }

    @RawResponse
    @GetMapping("/goods/{code}")
    public Mono<ResponseEntity<JsonNode>> goods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(() -> api.goods(tenant, code));
    }

    @RawResponse
    @PostMapping("/orders/{code}")
    public Mono<ResponseEntity<JsonNode>> placeOrder(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd body) {
        return respond(() -> api.placeOrder(tenant, code, body));
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/payment")
    public Mono<ResponseEntity<JsonNode>> confirmPayment(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo, @RequestBody JsonNode body) {
        return respond(() -> api.confirmPayment(tenant, orderNo, body.toString()));
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/cancel")
    public Mono<ResponseEntity<JsonNode>> cancelOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        return respond(() -> api.cancelOrder(tenant, orderNo));
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/fulfill")
    public Mono<ResponseEntity<JsonNode>> fulfillOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        return respond(() -> api.fulfillOrder(tenant, orderNo));
    }

    @RawResponse
    @GetMapping("/orders/{code}")
    public Mono<ResponseEntity<JsonNode>> order(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(() -> api.order(tenant, code));
    }

    private static Mono<ResponseEntity<JsonNode>> respond(Supplier<Reply> operation) {
        return Mono.fromSupplier(() -> {
            Reply reply = operation.get();
            return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
