package io.ddd4j.sample.micronaut.shiro;

import io.micronaut.core.annotation.Nullable;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.micronaut.context.annotation.Context;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/**
 * Micronaut 商品交易 HTTP 入口，转发商品和订单请求到共用的交易用例。
 */
@Context
@Controller("/orders/catalog")
@ExecuteOn(TaskExecutors.BLOCKING)
public class CatalogTradeController {
    /**
     * 共用的商品交易用例入口。
     */
    private final CatalogApi catalog;

    /**
     * 按运行框架提供的连接配置初始化商品交易 HTTP 入口。
     *
     * @param url       商品交易数据库的 JDBC 地址
     * @param username  数据库账号
     * @param password  数据库账号密码
     * @param publisher 商品交易领域事件发布器
     */
    public CatalogTradeController(
            @io.micronaut.context.annotation.Value("${catalog.jdbc.url}") String url,
            @io.micronaut.context.annotation.Value("${catalog.db.user}") String username,
            @io.micronaut.context.annotation.Value("${catalog.db.password}") String password,
            io.ddd4j.core.ddd.event.DomainEventPublisher publisher) {
        catalog = CatalogRuntime.start(url, username, password, publisher);
    }

    /**
     * 处理商品创建请求，将商品命令交给共用的商品交易用例。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内商品编码
     * @param input  商品创建数据
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @Post("/goods/{code}")
    public HttpResponse<String> createGoods(@Header("X-Tenant-Id") String tenant,
                                            @PathVariable("code") String code, @Body String input) {
        Reply reply = catalog.createGoods(tenant, code, input);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * 按租户及商品编码读取商品和规格。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内商品编码
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @Get("/goods/{code}")
    public HttpResponse<String> goods(@Header("X-Tenant-Id") String tenant,
                                      @PathVariable("code") String code) {
        Reply reply = catalog.goods(tenant, code);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * 执行下单命令，使用路径中的订单号标识新订单。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内业务订单号
     * @param input  下单数据
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @Post("/orders/{code}")
    public HttpResponse<String> placeOrder(@Header("X-Tenant-Id") String tenant,
                                           @PathVariable("code") String code, @Body String input) {
        Reply reply = catalog.placeOrder(tenant, code, input);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
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
    @Post("/orders/{orderNo}/payment")
    public HttpResponse<String> confirmPayment(@Nullable @Header("Idempotency-Key") String idempotencyKey,
                                               @Header("X-Tenant-Id") String tenant,
                                               @PathVariable("orderNo") String orderNo, @Body String body) {
        Reply reply = catalog.confirmPayment(idempotencyKey, tenant, orderNo, body);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus()))
                .contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * 提交取消订单命令。
     *
     * @param tenant  请求头提供的租户标识
     * @param orderNo 租户内业务订单号
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @Post("/orders/{orderNo}/cancel")
    public HttpResponse<String> cancel(@Header("X-Tenant-Id") String tenant, @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.cancelOrder(tenant, orderNo);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus()))
                .contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * 提交完成履约命令。
     *
     * @param tenant  请求头提供的租户标识
     * @param orderNo 租户内业务订单号
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @Post("/orders/{orderNo}/fulfill")
    public HttpResponse<String> fulfillOrder(@Header("X-Tenant-Id") String tenant, @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.fulfillOrder(tenant, orderNo);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus()))
                .contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * 按租户和订单号查询订单。
     *
     * @param tenant 请求头提供的租户标识
     * @param code   租户内业务订单号
     * @return 包含业务状态码和 JSON 正文的 HTTP 响应
     */
    @Get("/orders/{code}")
    public HttpResponse<String> order(@Header("X-Tenant-Id") String tenant,
                                      @PathVariable("code") String code) {
        Reply reply = catalog.order(tenant, code);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }
}
