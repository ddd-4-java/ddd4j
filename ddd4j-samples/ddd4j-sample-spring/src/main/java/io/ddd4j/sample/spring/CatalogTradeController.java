package io.ddd4j.sample.spring;

import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.spring.event.SpringDomainEventPublisher;
import org.springframework.context.ApplicationContext;
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
import org.springframework.web.context.WebApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;

import java.util.Map;

@Profile("catalog-trade")

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogTradeController {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CatalogApi api;
    private final boolean servletApplication;

    public CatalogTradeController(ApplicationContext context, ApplicationEventPublisher publisher) {
        servletApplication = context instanceof WebApplicationContext;
        var environment = context.getEnvironment();
        api = CatalogRuntime.start(environment.getRequiredProperty("CATALOG_JDBC_URL"),
                environment.getRequiredProperty("CATALOG_DB_USER"), environment.getRequiredProperty("CATALOG_DB_PASSWORD"),
                new SpringDomainEventPublisher(publisher));
    }

    @RawResponse
    @PostMapping("/goods/{code}")
    public ResponseEntity<Object> createGoods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd body) {
        return respond(api.createGoods(tenant, code, body));
    }

    @RawResponse
    @GetMapping("/goods/{code}")
    public ResponseEntity<Object> goods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(api.goods(tenant, code));
    }

    @RawResponse
    @PostMapping("/orders/{code}")
    public ResponseEntity<Object> placeOrder(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd body) {
        return respond(api.placeOrder(tenant, code, body));
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/payment")
    public ResponseEntity<Object> confirmPayment(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo, @RequestBody Map<String, Object> body) {
        return respond(api.confirmPayment(tenant, orderNo, JSON.valueToTree(body).toString()));
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/cancel")
    public ResponseEntity<Object> cancelOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        return respond(api.cancelOrder(tenant, orderNo));
    }

    @RawResponse
    @PostMapping("/orders/{orderNo}/fulfill")
    public ResponseEntity<Object> fulfillOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        return respond(api.fulfillOrder(tenant, orderNo));
    }

    @RawResponse
    @GetMapping("/orders/{code}")
    public ResponseEntity<Object> order(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(api.order(tenant, code));
    }

    private ResponseEntity<Object> respond(Reply reply) {
        Object payload = servletApplication ? reply.getPayload() : JSON.convertValue(reply.getPayload(), Object.class);
        return ResponseEntity.status(reply.getStatus()).body(payload);
    }
}
