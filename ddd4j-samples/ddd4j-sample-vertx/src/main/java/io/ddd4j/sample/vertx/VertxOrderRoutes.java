package io.ddd4j.sample.vertx;

import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.vertx.Ddd4jVertxContext;
import io.ddd4j.web.vertx.Ddd4jVertxWeb;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Vert.x HTTP 到共享 Order Application 的协议适配层。
 */
public final class VertxOrderRoutes {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private VertxOrderRoutes() {
    }

    public static Router router(Vertx vertx, OrderApplicationService applicationService) {
        Vertx actualVertx = Objects.requireNonNull(vertx, "vertx must not be null");
        OrderApplicationService service = Objects.requireNonNull(applicationService,
                "applicationService must not be null");
        Router router = Router.router(actualVertx);
        // Vert.x requires body parsing to be the first global handler.
        router.route().handler(BodyHandler.create());
        new Ddd4jVertxWeb(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/api/auth/**"),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, Json::encode).install(router);
        router.get("/health").handler(context -> respond(context, 200, R.ok("UP")));
        router.post("/api/auth/tokens/:userId").handler(context -> execute(context, () -> {
            Subject subject = SubjectKit.getSubject();
            return R.ok(new TokenResponse(subject.login(AuthRequest.of(context.pathParam("userId")))));
        }, 200));
        router.post("/api/orders").handler(context -> execute(context, () -> {
            JsonObject request = body(context);
            OrderReadModel order = service.find(service.create(new CreateOrderCommand(
                    request.getString("orderNo"), request.getString("buyerId"), request.getString("buyerName"))).id());
            return R.ok(order);
        }, 201));
        router.get("/api/orders/:orderId").handler(context -> execute(context,
                () -> R.ok(service.find(context.pathParam("orderId"))), 200));
        router.post("/api/orders/:orderId/lines").handler(context -> execute(context, () -> {
            JsonObject request = body(context);
            service.addLine(new AddOrderLineCommand(context.pathParam("orderId"), request.getString("goodsId"),
                    request.getString("goodsName"), request.getInteger("quantity"),
                    new BigDecimal(request.getValue("unitPrice").toString())));
            return R.ok(service.find(context.pathParam("orderId")));
        }, 200));
        router.post("/api/orders/:orderId/pay").handler(context -> execute(context, () -> {
            String idempotencyKey = context.request().getHeader(WebHeaders.IDEMPOTENCY_KEY);
            if (StrKit.isBlank(idempotencyKey)) {
                throw new WebStatusException(400, "Idempotency-Key is required");
            }
            service.pay(context.pathParam("orderId"), idempotencyKey);
            return R.ok(service.find(context.pathParam("orderId")));
        }, 200));
        return router;
    }

    private static JsonObject body(RoutingContext context) {
        JsonObject body = context.body().asJsonObject();
        if (Objects.isNull(body)) {
            throw new WebStatusException(400, "JSON request body is required");
        }
        return body;
    }

    private static <T> void execute(RoutingContext context, Callable<T> operation, int status) {
        Ddd4jVertxContext.executeBlocking(context, operation)
                .onComplete(result -> context.vertx().runOnContext(ignored -> {
                    if (result.failed()) {
                        context.fail(result.cause());
                        return;
                    }
                    respond(context, status, result.result());
                }));
    }

    private static void respond(RoutingContext context, int status, Object response) {
        context.response().setStatusCode(status).putHeader("Content-Type", JSON_CONTENT_TYPE)
                .end(Json.encode(response));
    }

    public static final class TokenResponse {
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
