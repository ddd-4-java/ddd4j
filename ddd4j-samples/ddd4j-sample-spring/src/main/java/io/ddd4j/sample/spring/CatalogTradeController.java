package io.ddd4j.sample.spring;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;

/** Spring MVC \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@Profile("catalog-trade")
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogTradeController {
    /** 保存 <code>REPLY JSON</code> 对应的 <code>JsonMapper</code> 数据。 */
    private static final JsonMapper REPLY_JSON = new JsonMapper();

    /** \u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c\u3002 */
    private final CatalogApi api;

    /**
     * \u6309\u8fd0\u884c\u6846\u67b6\u63d0\u4f9b\u7684\u8fde\u63a5\u914d\u7f6e\u521d\u59cb\u5316\u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\u3002
     * @param environment \u63d0\u4f9b\u6570\u636e\u5e93\u8fde\u63a5\u914d\u7f6e\u7684 Spring \u73af\u5883
     * @param publisher \u5546\u54c1\u4ea4\u6613\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     */
    public CatalogTradeController(org.springframework.core.env.Environment environment, ApplicationEventPublisher publisher) {
        api = CatalogRuntime.start(environment.getRequiredProperty("CATALOG_JDBC_URL"),
                environment.getRequiredProperty("CATALOG_DB_USER"), environment.getRequiredProperty("CATALOG_DB_PASSWORD"),
                new SpringDomainEventPublisher(publisher));
    }

    /**
     * \u5904\u7406\u5546\u54c1\u521b\u5efa\u8bf7\u6c42\uff0c\u5c06\u5546\u54c1\u547d\u4ee4\u4ea4\u7ed9\u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613\u7528\u4f8b\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u5546\u54c1\u7f16\u7801
     * @param body \u5546\u54c1\u521b\u5efa\u6570\u636e
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @PostMapping("/goods/{code}")
    public ResponseEntity<JsonNode> createGoods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd body) {
        return respond(api.createGoods(tenant, code, body));
    }

    /**
     * \u6309\u79df\u6237\u53ca\u5546\u54c1\u7f16\u7801\u8bfb\u53d6\u5546\u54c1\u548c\u89c4\u683c\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u5546\u54c1\u7f16\u7801
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @GetMapping("/goods/{code}")
    public ResponseEntity<JsonNode> goods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(api.goods(tenant, code));
    }

    /**
     * \u6267\u884c\u4e0b\u5355\u547d\u4ee4\uff0c\u4f7f\u7528\u8def\u5f84\u4e2d\u7684\u8ba2\u5355\u53f7\u6807\u8bc6\u65b0\u8ba2\u5355\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @param body \u4e0b\u5355\u6570\u636e
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @PostMapping("/orders/{code}")
    public ResponseEntity<JsonNode> placeOrder(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd body) {
        return respond(api.placeOrder(tenant, code, body));
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
        return respond(api.confirmPayment(idempotencyKey, tenant, orderNo, body.toString()));
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
        return respond(api.cancelOrder(tenant, orderNo));
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
        return respond(api.fulfillOrder(tenant, orderNo));
    }

    /**
     * \u6309\u79df\u6237\u548c\u8ba2\u5355\u53f7\u67e5\u8be2\u8ba2\u5355\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @RawResponse
    @GetMapping("/orders/{code}")
    public ResponseEntity<JsonNode> order(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(api.order(tenant, code));
    }

    /**
     * \u5c06\u5171\u7528 HTTP \u64cd\u4f5c\u7684\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u8f6c\u6210 Spring \u54cd\u5e94\u3002
     * @param reply \u5171\u7528 HTTP \u64cd\u4f5c\u8fd4\u56de\u7684\u72b6\u6001\u7801\u548c\u6b63\u6587
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    private static ResponseEntity<JsonNode> respond(Reply reply) {
        return ResponseEntity.status(reply.getStatus()).body(REPLY_JSON.readTree(reply.getBody()));
    }
}
