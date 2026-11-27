package io.ddd4j.sample.micronaut;

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

/** Micronaut \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@Context
@Controller("/api")
@ExecuteOn(TaskExecutors.BLOCKING)
public class CatalogTradeController {
    /** \u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c\u3002 */
    private final CatalogApi api;

    /**
     * \u6309\u8fd0\u884c\u6846\u67b6\u63d0\u4f9b\u7684\u8fde\u63a5\u914d\u7f6e\u521d\u59cb\u5316\u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\u3002
     * @param url \u5546\u54c1\u4ea4\u6613\u6570\u636e\u5e93\u7684 JDBC \u5730\u5740
     * @param username \u6570\u636e\u5e93\u8d26\u53f7
     * @param password \u6570\u636e\u5e93\u8d26\u53f7\u5bc6\u7801
     * @param publisher \u5546\u54c1\u4ea4\u6613\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     */
    public CatalogTradeController(
            @io.micronaut.context.annotation.Value("${catalog.jdbc.url}") String url,
            @io.micronaut.context.annotation.Value("${catalog.db.user}") String username,
            @io.micronaut.context.annotation.Value("${catalog.db.password}") String password,
            io.ddd4j.core.ddd.event.DomainEventPublisher publisher) {
        api = CatalogRuntime.start(url, username, password, publisher);
    }

    /**
     * \u5904\u7406\u5546\u54c1\u521b\u5efa\u8bf7\u6c42\uff0c\u5c06\u5546\u54c1\u547d\u4ee4\u4ea4\u7ed9\u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613\u7528\u4f8b\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u5546\u54c1\u7f16\u7801
     * @param body \u5546\u54c1\u521b\u5efa\u6570\u636e
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @Post("/goods/{code}")
    public HttpResponse<String> createGoods(@Header("X-Tenant-Id") String tenant, @PathVariable("code") String code, @Body String body) {
        Reply reply = api.createGoods(tenant, code, body);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * \u6309\u79df\u6237\u53ca\u5546\u54c1\u7f16\u7801\u8bfb\u53d6\u5546\u54c1\u548c\u89c4\u683c\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u5546\u54c1\u7f16\u7801
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @Get("/goods/{code}")
    public HttpResponse<String> goods(@Header("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        Reply reply = api.goods(tenant, code);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * \u6267\u884c\u4e0b\u5355\u547d\u4ee4\uff0c\u4f7f\u7528\u8def\u5f84\u4e2d\u7684\u8ba2\u5355\u53f7\u6807\u8bc6\u65b0\u8ba2\u5355\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @param body \u4e0b\u5355\u6570\u636e
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @Post("/orders/{code}")
    public HttpResponse<String> placeOrder(@Header("X-Tenant-Id") String tenant, @PathVariable("code") String code, @Body String body) {
        Reply reply = api.placeOrder(tenant, code, body);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * \u5411\u8ba2\u5355\u7528\u4f8b\u63d0\u4ea4\u5b9e\u4ed8\u91d1\u989d\u5e76\u8fd4\u56de\u786e\u8ba4\u7ed3\u679c\u3002
     * @param idempotencyKey 支付请求幂等键
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param orderNo \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @param body \u5e26\u4eba\u6c11\u5e01\u5206\u503c\u7684\u652f\u4ed8\u786e\u8ba4\u6570\u636e
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @Post("/orders/{orderNo}/payment")
    public HttpResponse<String> confirmPayment(@Nullable @Header("Idempotency-Key") String idempotencyKey,
            @Header("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo, @Body String body) {
        Reply reply = api.confirmPayment(idempotencyKey, tenant, orderNo, body);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus()))
                .contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * \u63d0\u4ea4\u53d6\u6d88\u8ba2\u5355\u547d\u4ee4\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param orderNo \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @Post("/orders/{orderNo}/cancel")
    public HttpResponse<String> cancelOrder(@Header("X-Tenant-Id") String tenant, @PathVariable("orderNo") String orderNo) {
        Reply reply = api.cancelOrder(tenant, orderNo);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus()))
                .contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * \u63d0\u4ea4\u5b8c\u6210\u5c65\u7ea6\u547d\u4ee4\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param orderNo \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @Post("/orders/{orderNo}/fulfill")
    public HttpResponse<String> fulfillOrder(@Header("X-Tenant-Id") String tenant, @PathVariable("orderNo") String orderNo) {
        Reply reply = api.fulfillOrder(tenant, orderNo);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus()))
                .contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    /**
     * \u6309\u79df\u6237\u548c\u8ba2\u5355\u53f7\u67e5\u8be2\u8ba2\u5355\u3002
     * @param tenant \u8bf7\u6c42\u5934\u63d0\u4f9b\u7684\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u4e1a\u52a1\u8ba2\u5355\u53f7
     * @return \u5305\u542b\u4e1a\u52a1\u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684 HTTP \u54cd\u5e94
     */
    @Get("/orders/{code}")
    public HttpResponse<String> order(@Header("X-Tenant-Id") String tenant, @PathVariable("code") String code) {
        Reply reply = api.order(tenant, code);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }
}
