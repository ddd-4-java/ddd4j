package io.ddd4j.sample.micronaut;

import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderStatus;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebStatusException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Micronaut HTTP 到共享 Order Application 的协议适配层。
 */
@Controller("/api/orders")
public class OrderController {

    private final OrderApplicationService applicationService;

    public OrderController(OrderApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    @Post
    public HttpResponse<R<OrderReadModel>> create(@Body CreateOrderRequest request) {
        OrderReadModel response = applicationService.find(applicationService.create(
                new CreateOrderCommand(request.orderNo(), request.buyerId(), request.buyerName())).id());
        return HttpResponse.created(R.ok(response));
    }

    @Get("/{orderId}")
    public R<OrderReadModel> find(@PathVariable String orderId) {
        return R.ok(applicationService.find(orderId));
    }

    @Get
    public R<List<OrderReadModel>> query(@QueryValue(defaultValue = "") String buyerId,
                                         @QueryValue(defaultValue = "") String status,
                                         @QueryValue(defaultValue = "1") int page,
                                         @QueryValue(defaultValue = "20") int size) {
        OrderStatus orderStatus = StrKit.isBlank(status) ? null : OrderStatus.valueOf(status.toUpperCase(Locale.ROOT));
        return R.ok(applicationService.query(new OrderQuery(
                StrKit.isBlank(buyerId) ? null : buyerId, orderStatus, page, size)));
    }

    @Post("/{orderId}/lines")
    public R<OrderReadModel> addLine(@PathVariable String orderId, @Body AddOrderLineRequest request) {
        applicationService.addLine(new AddOrderLineCommand(orderId, request.goodsId(), request.goodsName(),
                request.quantity(), request.unitPrice()));
        return R.ok(applicationService.find(orderId));
    }

    @Post("/{orderId}/pay")
    public R<OrderReadModel> pay(@PathVariable String orderId,
                                 @Header(WebHeaders.IDEMPOTENCY_KEY) String idempotencyKey) {
        if (StrKit.isBlank(idempotencyKey)) {
            throw new WebStatusException(400, "Idempotency-Key is required");
        }
        applicationService.pay(orderId, idempotencyKey);
        return R.ok(applicationService.find(orderId));
    }

    public record CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
    }

    public record AddOrderLineRequest(String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
    }
}
