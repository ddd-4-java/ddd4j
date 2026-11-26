package io.ddd4j.sample.spring.cqrs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.annotation.api.RawResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping(path = "/orders/catalog", produces = "application/json")
public class CatalogTradeController {
    private static final JsonMapper REPLY_JSON = new JsonMapper();

    private final CatalogCqrs catalog;
    private final ObjectMapper mapper = new ObjectMapper();

    public CatalogTradeController(CatalogCommandExecutor executor, CommandBus commands) {
        catalog = new CatalogCqrs(executor.api(), commands);
    }

    @RawResponse
    @PostMapping("/goods/{code}")
    public ResponseEntity<JsonNode> createGoods(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd input) throws com.fasterxml.jackson.core.JsonProcessingException {
        Reply reply = catalog.createGoods(tenant, code, mapper.writeValueAsString(input));
        return ResponseEntity.status(reply.getStatus()).body(REPLY_JSON.readTree(reply.getBody()));
    }

    @RawResponse
    @GetMapping("/goods/{code}")
    public ResponseEntity<JsonNode> goods(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.goods(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(REPLY_JSON.readTree(reply.getBody()));
    }

    @RawResponse
    @PostMapping("/orders/{code}")
    public ResponseEntity<JsonNode> placeOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd input) throws com.fasterxml.jackson.core.JsonProcessingException {
        Reply reply = catalog.placeOrder(tenant, code, mapper.writeValueAsString(input));
        return ResponseEntity.status(reply.getStatus()).body(REPLY_JSON.readTree(reply.getBody()));
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/payment")
    public ResponseEntity<JsonNode> confirmPayment(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo, @RequestBody JsonNode body) {
        Reply reply = catalog.confirmPayment(tenant, orderNo, body.toString());
        return ResponseEntity.status(reply.getStatus()).body(REPLY_JSON.readTree(reply.getBody()));
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/cancel")
    public ResponseEntity<JsonNode> cancelOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.cancelOrder(tenant, orderNo);
        return ResponseEntity.status(reply.getStatus()).body(REPLY_JSON.readTree(reply.getBody()));
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/fulfill")
    public ResponseEntity<JsonNode> fulfillOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.fulfillOrder(tenant, orderNo);
        return ResponseEntity.status(reply.getStatus()).body(REPLY_JSON.readTree(reply.getBody()));
    }

    @RawResponse
    @GetMapping("/orders/{code}")
    public ResponseEntity<JsonNode> order(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.order(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(REPLY_JSON.readTree(reply.getBody()));
    }

    @RawResponse
    @PostMapping("/projections/orders/{code}")
    public ResponseEntity<JsonNode> project(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.project(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(REPLY_JSON.readTree(reply.getBody()));
    }
}
