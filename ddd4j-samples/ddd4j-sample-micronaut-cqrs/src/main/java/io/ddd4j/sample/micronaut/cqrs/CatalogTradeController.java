package io.ddd4j.sample.micronaut.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.Reply;
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
    private final CatalogCqrs catalog;

    public CatalogTradeController(CatalogCommandExecutor executor, CommandBus commands) {
        catalog = new CatalogCqrs(executor.api(), commands);
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
    public HttpResponse<String> cancelOrder(@Header("X-Tenant-Id") String tenant, @PathVariable("orderNo") String orderNo) {
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

    @Post("/projections/orders/{code}")
    public HttpResponse<String> project(@Header("X-Tenant-Id") String tenant,
            @PathVariable("code") String code) {
        Reply reply = catalog.project(tenant, code);
        return HttpResponse.<String>status(HttpStatus.valueOf(reply.getStatus())).contentType(MediaType.APPLICATION_JSON_TYPE).body(reply.getBody());
    }
}
