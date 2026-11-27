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

/** Spring MVC \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@Profile("catalog-trade")

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogTradeController {
    /** 保存 <code>JSON</code> 对应的 <code>ObjectMapper</code> 数据。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** \u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c\u3002 */
    private final CatalogApi api;
    /** 保存 <code>servlet Application</code> 对应的 <code>boolean</code> 数据。 */
    private final boolean servletApplication;

    /**
     * 初始化 <code>CatalogTradeController</code> 并委托其默认构造路径。
     *
     * @param context 传入此操作的 <code>ApplicationContext</code> 类型 <code>context</code>
     * @param publisher 传入此操作的 <code>ApplicationEventPublisher</code> 类型 <code>publisher</code>
     */
    public CatalogTradeController(ApplicationContext context, ApplicationEventPublisher publisher) {
        servletApplication = context instanceof WebApplicationContext;
        var environment = context.getEnvironment();
        api = CatalogRuntime.start(environment.getRequiredProperty("CATALOG_JDBC_URL"),
                environment.getRequiredProperty("CATALOG_DB_USER"), environment.getRequiredProperty("CATALOG_DB_PASSWORD"),
                new SpringDomainEventPublisher(publisher));
    }

    /**
     * 执行 <code>create Goods</code>，调用 <code>api.createGoods</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param code 随响应返回的业务状态编码
     * @param body 此 HTTP 消息携带的正文
     * @return 返回表达式 <code>respond(api.createGoods(tenant, code, body))</code>
     */
    @RawResponse
    @PostMapping("/goods/{code}")
    public ResponseEntity<Object> createGoods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd body) {
        return respond(api.createGoods(tenant, code, body));
    }

    /**
     * 执行 <code>goods</code>，调用 <code>api.goods</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param code 随响应返回的业务状态编码
     * @return 返回表达式 <code>respond(api.goods(tenant, code))</code>
     */
    @RawResponse
    @GetMapping("/goods/{code}")
    public ResponseEntity<Object> goods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(api.goods(tenant, code));
    }

    /**
     * 执行 <code>place Order</code>，调用 <code>api.placeOrder</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param code 随响应返回的业务状态编码
     * @param body 此 HTTP 消息携带的正文
     * @return 返回表达式 <code>respond(api.placeOrder(tenant, code, body))</code>
     */
    @RawResponse
    @PostMapping("/orders/{code}")
    public ResponseEntity<Object> placeOrder(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd body) {
        return respond(api.placeOrder(tenant, code, body));
    }

    /**
     * 执行 <code>confirm Payment</code>，调用 <code>api.confirmPayment</code>、<code>JSON.valueToTree</code>。
     *
     * @param idempotencyKey 支付请求幂等键
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param orderNo 传入此操作的 <code>String</code> 类型 <code>orderNo</code>
     * @param body 此 HTTP 消息携带的正文
     * @return 返回表达式 <code>respond(api.confirmPayment(tenant, orderNo, JSON.valueToTree(body).toString()))</code>
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/payment")
    public ResponseEntity<Object> confirmPayment(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo, @RequestBody Map<String, Object> body) {
        return respond(api.confirmPayment(idempotencyKey, tenant, orderNo, JSON.valueToTree(body).toString()));
    }

    /**
     * 执行 <code>cancel Order</code>，调用 <code>api.cancelOrder</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param orderNo 传入此操作的 <code>String</code> 类型 <code>orderNo</code>
     * @return 返回表达式 <code>respond(api.cancelOrder(tenant, orderNo))</code>
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/cancel")
    public ResponseEntity<Object> cancelOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        return respond(api.cancelOrder(tenant, orderNo));
    }

    /**
     * 执行 <code>fulfill Order</code>，调用 <code>api.fulfillOrder</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param orderNo 传入此操作的 <code>String</code> 类型 <code>orderNo</code>
     * @return 返回表达式 <code>respond(api.fulfillOrder(tenant, orderNo))</code>
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/fulfill")
    public ResponseEntity<Object> fulfillOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        return respond(api.fulfillOrder(tenant, orderNo));
    }

    /**
     * 执行 <code>order</code>，调用 <code>api.order</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param code 随响应返回的业务状态编码
     * @return 返回表达式 <code>respond(api.order(tenant, code))</code>
     */
    @RawResponse
    @GetMapping("/orders/{code}")
    public ResponseEntity<Object> order(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(api.order(tenant, code));
    }

    /**
     * \u5c06\u5171\u7528 HTTP \u64cd\u4f5c\u7684\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u8f6c\u6210 Spring \u54cd\u5e94\u3002
     * @param reply \u5171\u7528 HTTP \u64cd\u4f5c\u8fd4\u56de\u7684\u72b6\u6001\u7801\u548c\u6b63\u6587
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    private ResponseEntity<Object> respond(Reply reply) {
        Object payload = servletApplication ? reply.getPayload() : JSON.convertValue(reply.getPayload(), Object.class);
        return ResponseEntity.status(reply.getStatus()).body(payload);
    }
}
