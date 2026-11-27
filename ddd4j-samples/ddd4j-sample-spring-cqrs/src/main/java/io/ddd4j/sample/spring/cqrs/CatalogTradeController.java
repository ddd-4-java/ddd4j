package io.ddd4j.sample.spring.cqrs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Spring MVC 商品交易 HTTP 入口，接入示例的命令总线及订单投影。
 */
@RestController
@RequestMapping(path = "/orders/catalog", produces = "application/json")
public class CatalogTradeController {
    /**
     * 共用的商品交易用例入口。
     */
    private final CatalogCqrs catalog;
    /**
     * 用于转换商品交易 HTTP 正文的 JSON 映射器。
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 使用给定命令执行器和命令总线装配商品交易入口。
     *
     * @param executor 注册到命令总线的商品交易命令执行器
     * @param commands 示例框架装配的命令总线
     */
    public CatalogTradeController(CatalogCommandExecutor executor, CommandBus commands) {
        catalog = new CatalogCqrs(executor.api(), commands);
    }

    /**
     * 处理商品创建请求，将商品命令交给共用的商品交易用例。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内商品编码
     * @param input  商品创建数据
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     * @throws com.fasterxml.jackson.core.JsonProcessingException 无法将命令编码为 JSON
     */
    @RawResponse
    @PostMapping("/goods/{code}")
    public ResponseEntity<JsonNode> createGoods(@RequestHeader("X-Tenant-Id") String tenant,
                                                @PathVariable("code") String code, @RequestBody CreateGoodsSpuCmd input) throws com.fasterxml.jackson.core.JsonProcessingException {
        Reply reply = catalog.createGoods(tenant, code, mapper.writeValueAsString(input));
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
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
    public ResponseEntity<JsonNode> goods(@RequestHeader("X-Tenant-Id") String tenant,
                                          @PathVariable("code") String code) {
        Reply reply = catalog.goods(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * 执行下单命令，使用路径中的订单号标识新订单。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内业务订单号
     * @param input  下单数据
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     * @throws com.fasterxml.jackson.core.JsonProcessingException 无法将命令编码为 JSON
     */
    @RawResponse
    @PostMapping("/orders/{code}")
    public ResponseEntity<JsonNode> placeOrder(@RequestHeader("X-Tenant-Id") String tenant,
                                               @PathVariable("code") String code, @RequestBody PlaceTradeOrderCmd input) throws com.fasterxml.jackson.core.JsonProcessingException {
        Reply reply = catalog.placeOrder(tenant, code, mapper.writeValueAsString(input));
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * 向订单用例提交实付金额并返回确认结果。
     *
     * @param tenant  请求头提供的租户标识
     * @param orderNo 租户内业务订单号
     * @param body    带人民币分值的支付确认数据
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @PostMapping("/orders/{orderNo}/payment")
    public ResponseEntity<JsonNode> confirmPayment(@RequestHeader("X-Tenant-Id") String tenant,
                                                   @PathVariable("orderNo") String orderNo, @RequestBody JsonNode body) {
        Reply reply = catalog.confirmPayment(tenant, orderNo, body.toString());
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
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
        Reply reply = catalog.cancelOrder(tenant, orderNo);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
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
        Reply reply = catalog.fulfillOrder(tenant, orderNo);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * 按租户和订单号查询订单投影。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内业务订单号
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @GetMapping("/orders/{code}")
    public ResponseEntity<JsonNode> order(@RequestHeader("X-Tenant-Id") String tenant,
                                          @PathVariable("code") String code) {
        Reply reply = catalog.order(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }

    /**
     * 通过读取已提交订单重建订单投影。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内业务订单号
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @RawResponse
    @PostMapping("/projections/orders/{code}")
    public ResponseEntity<JsonNode> project(@RequestHeader("X-Tenant-Id") String tenant,
                                            @PathVariable("code") String code) {
        Reply reply = catalog.project(tenant, code);
        return ResponseEntity.status(reply.getStatus()).body(reply.getPayload());
    }
}
