package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.annotation.api.RawResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Spring MVC \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u63a5\u5165\u793a\u4f8b\u7684\u547d\u4ee4\u603b\u7ebf\u53ca\u8ba2\u5355\u6295\u5f71\u3002 */
@RestController
@RequestMapping(path = "/orders/catalog", produces = "application/json")
public class CatalogTradeController {
    /** \u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613\u7528\u4f8b\u5165\u53e3\u3002 */
    private final CatalogCqrs catalog;
    /** \u7528\u4e8e\u8f6c\u6362\u5546\u54c1\u4ea4\u6613 HTTP \u6b63\u6587\u7684 JSON \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * \u4f7f\u7528\u7ed9\u5b9a\u547d\u4ee4\u6267\u884c\u5668\u548c\u547d\u4ee4\u603b\u7ebf\u88c5\u914d\u5546\u54c1\u4ea4\u6613\u5165\u53e3\u3002
     * @param executor \u6ce8\u518c\u5230\u547d\u4ee4\u603b\u7ebf\u7684\u5546\u54c1\u4ea4\u6613\u547d\u4ee4\u6267\u884c\u5668
     * @param commands \u793a\u4f8b\u6846\u67b6\u88c5\u914d\u7684\u547d\u4ee4\u603b\u7ebf
     */
    public CatalogTradeController(CatalogCommandExecutor executor, CommandBus commands) {
        catalog = new CatalogCqrs(executor.api(), commands);
    }

    /**
     * \u5904\u7406\u5546\u54c1\u521b\u5efa\u8bf7\u6c42\uff0c\u5c06\u5546\u54c1\u547d\u4ee4\u4ea4\u7ed9\u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613\u7528\u4f8b\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u5546\u54c1\u7f16\u7801
     * @param input \u5546\u54c1\u521b\u5efa\u6570\u636e
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     * @throws com.fasterxml.jackson.core.JsonProcessingException \u65e0\u6cd5\u5c06\u547d\u4ee4\u7f16\u7801\u4e3a JSON
     */
    @RawResponse
    @PostMapping("/goods/{code}")
    public ResponseEntity<JsonNode> createGoods(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd input) throws com.fasterxml.jackson.core.JsonProcessingException {
        Reply reply = catalog.createGoods(tenant, code, mapper.writeValueAsString(input));
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * \u6309\u79df\u6237\u53ca\u5546\u54c1\u7f16\u7801\u8bfb\u53d6\u5546\u54c1\u548c\u89c4\u683c\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u5546\u54c1\u7f16\u7801
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @GetMapping("/goods/{code}")
    public ResponseEntity<JsonNode> goods(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.goods(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * \u6267\u884c\u4e0b\u5355\u547d\u4ee4\uff0c\u4f7f\u7528\u8def\u5f84\u4e2d\u7684\u8ba2\u5355\u53f7\u6807\u8bc6\u65b0\u8ba2\u5355\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @param input \u4e0b\u5355\u6570\u636e
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     * @throws com.fasterxml.jackson.core.JsonProcessingException \u65e0\u6cd5\u5c06\u547d\u4ee4\u7f16\u7801\u4e3a JSON
     */
    @RawResponse
    @PostMapping("/orders/{code}")
    public ResponseEntity<JsonNode> placeOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd input) throws com.fasterxml.jackson.core.JsonProcessingException {
        Reply reply = catalog.placeOrder(tenant, code, mapper.writeValueAsString(input));
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * \u5411\u8ba2\u5355\u7528\u4f8b\u63d0\u4ea4\u5b9e\u4ed8\u91d1\u989d\u5e76\u8fd4\u56de\u786e\u8ba4\u7ed3\u679c\u3002
     * @param idempotencyKey 支付请求幂等键
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param orderNo \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @param body \u5e26\u4eba\u6c11\u5e01\u5206\u503c\u7684\u652f\u4ed8\u786e\u8ba4\u6570\u636e
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/payment")
    public ResponseEntity<JsonNode> confirmPayment(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo, @RequestBody JsonNode body) {
        Reply reply = catalog.confirmPayment(idempotencyKey, tenant, orderNo, body.toString());
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * \u63d0\u4ea4\u53d6\u6d88\u8ba2\u5355\u547d\u4ee4\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param orderNo \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/cancel")
    public ResponseEntity<JsonNode> cancelOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.cancelOrder(tenant, orderNo);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * \u63d0\u4ea4\u5b8c\u6210\u5c65\u7ea6\u547d\u4ee4\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param orderNo \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/fulfill")
    public ResponseEntity<JsonNode> fulfillOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.fulfillOrder(tenant, orderNo);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * \u6309\u79df\u6237\u548c\u8ba2\u5355\u53f7\u67e5\u8be2\u8ba2\u5355\u6295\u5f71\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @GetMapping("/orders/{code}")
    public ResponseEntity<JsonNode> order(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.order(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * \u901a\u8fc7\u8bfb\u53d6\u5df2\u63d0\u4ea4\u8ba2\u5355\u91cd\u5efa\u8ba2\u5355\u6295\u5f71\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @PostMapping("/projections/orders/{code}")
    public ResponseEntity<JsonNode> project(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.project(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }
}
