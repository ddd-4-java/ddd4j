package io.ddd4j.sample.spring;

import com.fasterxml.jackson.databind.JsonNode;
import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.spring.event.SpringDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Spring MVC 商品交易 HTTP 入口，转发商品和订单请求到共用的交易用例。
 */
@Profile("catalog-trade")
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogTradeController {
    /**
     * 共用的商品交易 HTTP 操作。
     */
    private final CatalogApi api;

    /**
     * 按运行框架提供的连接配置初始化商品交易 HTTP 入口。
     *
     * @param environment 提供数据库连接配置的 Spring 环境
     * @param publisher   商品交易领域事件发布器
     */
    public CatalogTradeController(org.springframework.core.env.Environment environment, ApplicationEventPublisher publisher) {
        api = CatalogRuntime.start(environment.getRequiredProperty("CATALOG_JDBC_URL"),
                environment.getRequiredProperty("CATALOG_DB_USER"), environment.getRequiredProperty("CATALOG_DB_PASSWORD"),
                new SpringDomainEventPublisher(publisher));
    }

    /**
     * 将共用 HTTP 操作的状态码和 JSON 正文转成 Spring 响应。
     *
     * @param reply 共用 HTTP 操作返回的状态码和正文
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    private static ResponseEntity<JsonNode> respond(Reply reply) {
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * 处理商品创建请求，将商品命令交给共用的商品交易用例。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内商品编码
     * @param body   商品创建数据
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @PostMapping("/goods/{code}")
    public ResponseEntity<JsonNode> createGoods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd body) {
        return respond(api.createGoods(tenant, code, body));
    }

    /**
     * 按租户及商品编码读取商品和规格。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内商品编码
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @GetMapping("/goods/{code}")
    public ResponseEntity<JsonNode> goods(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(api.goods(tenant, code));
    }

    /**
     * 执行下单命令，使用路径中的订单号标识新订单。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内业务订单号
     * @param body   下单数据
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @PostMapping("/orders/{code}")
    public ResponseEntity<JsonNode> placeOrder(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd body) {
        return respond(api.placeOrder(tenant, code, body));
    }

    /**
     * 向订单用例提交实付金额并返回确认结果。
     *
     * @param idempotencyKey 支付请求幂等键
     * @param tenant  请求头提供的租户标识
     * @param orderNo 租户内业务订单号
     * @param body    带人民币分值的支付确认数据
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/payment")
    public ResponseEntity<JsonNode> confirmPayment(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                   @RequestHeader("X-Tenant-Id") String tenant,
                                                   @PathVariable("orderNo") String orderNo, @RequestBody JsonNode body) {
        return respond(api.confirmPayment(idempotencyKey, tenant, orderNo, body.toString()));
    }

    /**
     * 提交取消订单命令。
     *
     * @param tenant  请求头提供的租户标识
     * @param orderNo 租户内业务订单号
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/cancel")
    public ResponseEntity<JsonNode> cancelOrder(@RequestHeader("X-Tenant-Id") String tenant,
                                                @PathVariable("orderNo") String orderNo) {
        return respond(api.cancelOrder(tenant, orderNo));
    }

    /**
     * 提交完成履约命令。
     *
     * @param tenant  请求头提供的租户标识
     * @param orderNo 租户内业务订单号
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/fulfill")
    public ResponseEntity<JsonNode> fulfillOrder(@RequestHeader("X-Tenant-Id") String tenant,
                                                 @PathVariable("orderNo") String orderNo) {
        return respond(api.fulfillOrder(tenant, orderNo));
    }

    /**
     * 按租户和订单号查询订单。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内业务订单号
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @GetMapping("/orders/{code}")
    public ResponseEntity<JsonNode> order(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        return respond(api.order(tenant, code));
    }
}
