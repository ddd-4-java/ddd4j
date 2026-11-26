package io.ddd4j.sample.spring.satoken;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.spring.event.SpringDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import io.ddd4j.annotation.api.RawResponse;
import com.fasterxml.jackson.databind.JsonNode;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/orders/catalog", produces = "application/json")
public class CatalogTradeController {
    private final CatalogApi catalog;

    public CatalogTradeController(org.springframework.core.env.Environment environment, ApplicationEventPublisher publisher) {
        catalog = CatalogRuntime.start(environment.getRequiredProperty("CATALOG_JDBC_URL"),
                environment.getRequiredProperty("CATALOG_DB_USER"), environment.getRequiredProperty("CATALOG_DB_PASSWORD"),
                new SpringDomainEventPublisher(publisher));
    }

    @RawResponse
    @PostMapping("/goods/{code}")
    public ResponseEntity<JsonNode> createGoods(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd input) {
        Reply reply = catalog.createGoods(tenant, code, input);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    @RawResponse
    @GetMapping("/goods/{code}")
    public ResponseEntity<JsonNode> goods(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.goods(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    @RawResponse
    @PostMapping("/orders/{code}")
    public ResponseEntity<JsonNode> placeOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd input) {
        Reply reply = catalog.placeOrder(tenant, code, input);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/payment")
    public ResponseEntity<JsonNode> confirmPayment(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo, @RequestBody JsonNode body) {
        Reply reply = catalog.confirmPayment(tenant, orderNo, body.toString());
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/cancel")
    public ResponseEntity<JsonNode> cancelOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.cancelOrder(tenant, orderNo);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/fulfill")
    public ResponseEntity<JsonNode> fulfillOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.fulfillOrder(tenant, orderNo);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    @RawResponse
    @GetMapping("/orders/{code}")
    public ResponseEntity<JsonNode> order(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.order(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }
}
