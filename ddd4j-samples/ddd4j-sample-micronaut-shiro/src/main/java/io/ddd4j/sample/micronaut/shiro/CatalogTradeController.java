package io.ddd4j.sample.micronaut.shiro;

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

@Context
@Controller("/orders/catalog")
@ExecuteOn(TaskExecutors.BLOCKING)
public class CatalogTradeController {
    private final CatalogApi catalog;

    public CatalogTradeController(
            @io.micronaut.context.annotation.Value("${catalog.jdbc.url}") String url,
            @io.micronaut.context.annotation.Value("${catalog.db.user}") String username,
            @io.micronaut.context.annotation.Value("${catalog.db.password}") String password,
            io.ddd4j.core.ddd.event.DomainEventPublisher publisher) {
        catalog = CatalogRuntime.start(url, username, password, publisher);
    }

    @Post("/goods/{code}")
    public HttpResponse<String> createGoods(@Header("X-Tenant-Id") String tenant,
            @PathVariable("code") String code, @Body String input) {
        Reply reply = catalog.createGoods(tenant, code, input);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    @Get("/goods/{code}")
    public HttpResponse<String> goods(@Header("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.goods(tenant, code);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    @Post("/orders/{code}")
    public HttpResponse<String> placeOrder(@Header("X-Tenant-Id") String tenant,
            @PathVariable("code") String code, @Body String input) {
        Reply reply = catalog.placeOrder(tenant, code, input);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    @Post("/orders/{orderNo}/payment")
    public HttpResponse<String> confirmPayment(@Header("X-Tenant-Id") String tenant,
            @PathVariable("orderNo") String orderNo, @Body String body) {
        Reply reply = catalog.confirmPayment(tenant, orderNo, body);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus()))
                .contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    @Post("/orders/{orderNo}/cancel")
    public HttpResponse<String> cancel(@Header("X-Tenant-Id") String tenant, @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.cancelOrder(tenant, orderNo);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus()))
                .contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    @Post("/orders/{orderNo}/fulfill")
    public HttpResponse<String> fulfillOrder(@Header("X-Tenant-Id") String tenant, @PathVariable("orderNo") String orderNo) {
        Reply reply = catalog.fulfillOrder(tenant, orderNo);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus()))
                .contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }

    @Get("/orders/{code}")
    public HttpResponse<String> order(@Header("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.order(tenant, code);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }
}
