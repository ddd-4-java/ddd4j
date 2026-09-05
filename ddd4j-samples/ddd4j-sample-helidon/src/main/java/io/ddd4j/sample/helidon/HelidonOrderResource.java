package io.ddd4j.sample.helidon;

import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.error.WebStatusException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Helidon JAX-RS 到共享 Order Application 的协议适配层。
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class HelidonOrderResource {

    private final OrderApplicationService applicationService;

    @Inject
    public HelidonOrderResource(OrderApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    @POST
    @Path("/auth/tokens/{userId}")
    public R<TokenResponse> issueToken(@PathParam("userId") String userId) {
        return R.ok(new TokenResponse(SubjectKit.getSubject().login(AuthRequest.of(userId))));
    }

    @POST
    @Path("/orders")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateOrderRequest request) {
        CreateOrderRequest actual = Objects.requireNonNull(request, "request must not be null");
        OrderReadModel order = applicationService.find(applicationService.create(new CreateOrderCommand(
                actual.getOrderNo(), actual.getBuyerId(), actual.getBuyerName())).id());
        return Response.status(Response.Status.CREATED).entity(R.ok(order)).build();
    }

    @GET
    @Path("/orders/{orderId}")
    public R<OrderReadModel> find(@PathParam("orderId") String orderId) {
        return R.ok(applicationService.find(orderId));
    }

    @POST
    @Path("/orders/{orderId}/lines")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<OrderReadModel> addLine(@PathParam("orderId") String orderId, AddOrderLineRequest request) {
        AddOrderLineRequest actual = Objects.requireNonNull(request, "request must not be null");
        applicationService.addLine(new AddOrderLineCommand(orderId, actual.getGoodsId(), actual.getGoodsName(),
                actual.getQuantity(), actual.getUnitPrice()));
        return R.ok(applicationService.find(orderId));
    }

    @POST
    @Path("/orders/{orderId}/pay")
    public R<OrderReadModel> pay(@PathParam("orderId") String orderId,
                                 @HeaderParam(WebHeaders.IDEMPOTENCY_KEY) String idempotencyKey) {
        if (StrKit.isBlank(idempotencyKey)) {
            throw new WebStatusException(400, "Idempotency-Key is required");
        }
        applicationService.pay(orderId, idempotencyKey);
        return R.ok(applicationService.find(orderId));
    }

    @Data
    public static class CreateOrderRequest {

        private String orderNo;
        private String buyerId;
        private String buyerName;
    }

    @Data
    public static class AddOrderLineRequest {

        private String goodsId;
        private String goodsName;
        private int quantity;
        private BigDecimal unitPrice;
    }public final class TokenResponse {
        private final String token;

        public TokenResponse(String token) {
            this.token = token;
        }
        public String token() { return token; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        TokenResponse other = (TokenResponse) o;
            return Objects.equals(this.token, other.token);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(token); }
        @Override
        public String toString() {
            return "TokenResponse{" + "token=" + token + "}";
        }
    
    }
}
