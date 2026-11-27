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

/** Spring MVC \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@Profile("catalog-trade")
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogTradeController {
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
     * 执行 <code>create Goods</code>，调用 <code>api.createGoods</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param code 随响应返回的业务状态编码
     * @param body 此 HTTP 消息携带的正文
     * @return 当前操作得到的 <code>Mono&lt;ResponseEntity&lt;JsonNode&gt;&gt;</code> 结果
     */
    @RawResponse
    @PostMapping("/goods/{code}")
    public Mono<ResponseEntity<JsonNode>> createGoods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd body) {
        return respond(() -> api.createGoods(tenant, code, body));
    }

    /**
     * 执行 <code>goods</code>，调用 <code>api.goods</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param code 随响应返回的业务状态编码
     * @return 当前操作得到的 <code>Mono&lt;ResponseEntity&lt;JsonNode&gt;&gt;</code> 结果
     */
    @RawResponse
    @GetMapping("/goods/{code}")
    public Mono<ResponseEntity<JsonNode>> goods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(() -> api.goods(tenant, code));
    }

    /**
     * 执行 <code>place Order</code>，调用 <code>api.placeOrder</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param code 随响应返回的业务状态编码
     * @param body 此 HTTP 消息携带的正文
     * @return 当前操作得到的 <code>Mono&lt;ResponseEntity&lt;JsonNode&gt;&gt;</code> 结果
     */
    @RawResponse
    @PostMapping("/orders/{code}")
    public Mono<ResponseEntity<JsonNode>> placeOrder(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd body) {
        return respond(() -> api.placeOrder(tenant, code, body));
    }

    /**
     * 执行 <code>confirm Payment</code>，调用 <code>api.confirmPayment</code>、<code>body.toString</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param orderNo 传入此操作的 <code>String</code> 类型 <code>orderNo</code>
     * @param body 此 HTTP 消息携带的正文
     * @return 当前操作得到的 <code>Mono&lt;ResponseEntity&lt;JsonNode&gt;&gt;</code> 结果
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/payment")
    public Mono<ResponseEntity<JsonNode>> confirmPayment(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo, @RequestBody JsonNode body) {
        return respond(() -> api.confirmPayment(tenant, orderNo, body.toString()));
    }

    /**
     * 执行 <code>cancel Order</code>，调用 <code>api.cancelOrder</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param orderNo 传入此操作的 <code>String</code> 类型 <code>orderNo</code>
     * @return 当前操作得到的 <code>Mono&lt;ResponseEntity&lt;JsonNode&gt;&gt;</code> 结果
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/cancel")
    public Mono<ResponseEntity<JsonNode>> cancelOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        return respond(() -> api.cancelOrder(tenant, orderNo));
    }

    /**
     * 执行 <code>fulfill Order</code>，调用 <code>api.fulfillOrder</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param orderNo 传入此操作的 <code>String</code> 类型 <code>orderNo</code>
     * @return 当前操作得到的 <code>Mono&lt;ResponseEntity&lt;JsonNode&gt;&gt;</code> 结果
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/fulfill")
    public Mono<ResponseEntity<JsonNode>> fulfillOrder(@RequestHeader("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo) {
        return respond(() -> api.fulfillOrder(tenant, orderNo));
    }

    /**
     * 执行 <code>order</code>，调用 <code>api.order</code>。
     *
     * @param tenant 传入此操作的 <code>String</code> 类型 <code>tenant</code>
     * @param code 随响应返回的业务状态编码
     * @return 当前操作得到的 <code>Mono&lt;ResponseEntity&lt;JsonNode&gt;&gt;</code> 结果
     */
    @RawResponse
    @GetMapping("/orders/{code}")
    public Mono<ResponseEntity<JsonNode>> order(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(() -> api.order(tenant, code));
    }

    /**
     * 执行 <code>respond</code>，调用 <code>Mono.fromSupplier</code>、<code>operation.get</code>、<code>ResponseEntity.status</code>。
     *
     * @param operation 传入此操作的 <code>Supplier&lt;Reply&gt;</code> 类型 <code>operation</code>
     * @return 返回表达式 <code>ResponseEntity.status(reply.getStatus()).body(reply.getPayload())</code>
     */
    private static Mono<ResponseEntity<JsonNode>> respond(Supplier<Reply> operation) {
        return Mono.fromSupplier(() -> {
            Reply reply = operation.get();
            return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
