/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.vertx;

import com.google.common.collect.ImmutableList;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.vertx.Ddd4jVertxContext;
import io.ddd4j.web.vertx.Ddd4jVertxWeb;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Vert.x 商品交易 HTTP 适配层。
 */
public final class VertxOrderRoutes {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private VertxOrderRoutes() {
    }

    public static Router router(Vertx vertx, CatalogEndpoints catalog, AuthenticationMode mode) {
        return router(vertx, catalog, mode, null);
    }

    static Router router(Vertx vertx, CatalogEndpoints catalog, AuthenticationMode mode,
                         VertxOrderApplication lifecycle) {
        Vertx actualVertx = Objects.requireNonNull(vertx, "vertx must not be null");
        Router router = Router.router(actualVertx);
        // Vert.x requires body parsing to be the first global handler.
        router.route().handler(BodyHandler.create());
        new Ddd4jVertxWeb(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(ImmutableList.of("/health", "/api/auth/**"),
                                Objects.requireNonNull(mode, "mode must not be null"))),
                new DefaultWebExceptionTranslator(), null, Json::encode).install(router);
        router.get("/health").handler(context -> respond(context, 200, R.ok("UP")));
        router.post("/api/auth/tokens/:userId").handler(context -> execute(lifecycle, context, () -> {
            Subject subject = SubjectKit.getSubject();
            return R.ok(new TokenResponse(subject.login(AuthRequest.of(context.pathParam("userId")))));
        }, 200));
        router.post("/api/goods/:code").handler(ctx -> trade(lifecycle, ctx, () -> catalog.createGoods(
                ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"), ctx.body().asString())));
        router.get("/api/goods/:code").handler(ctx -> trade(lifecycle, ctx, () -> catalog.goods(
                ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"))));
        router.post("/api/orders/:code").handler(ctx -> trade(lifecycle, ctx, () -> catalog.placeOrder(
                ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"), ctx.body().asString())));
        router.post("/api/orders/:code/payment").handler(ctx -> trade(lifecycle, ctx, () -> catalog.confirmPayment(
                ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"), ctx.body().asString())));
        router.post("/api/orders/:code/cancel").handler(ctx -> trade(lifecycle, ctx, () ->
                catalog.cancelOrder(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"))));
        router.post("/api/orders/:code/fulfill").handler(ctx -> trade(lifecycle, ctx, () ->
                catalog.fulfillOrder(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"))));
        router.get("/api/orders/:code").handler(ctx -> trade(lifecycle, ctx, () -> catalog.order(
                ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"))));
        return router;
    }

    private static void trade(VertxOrderApplication lifecycle, RoutingContext ctx, Callable<Reply> call) {
        Future<Reply> task = Objects.isNull(lifecycle) ? Ddd4jVertxContext.executeBlocking(ctx, call)
                : lifecycle.execute(ctx, call);
        task.onSuccess(reply -> ctx.response().setStatusCode(reply.getStatus())
                .putHeader("Content-Type", JSON_CONTENT_TYPE).end(reply.getBody())).onFailure(ctx::fail);
    }

    private static <T> void execute(VertxOrderApplication lifecycle, RoutingContext context, Callable<T> operation, int status) {
        Future<T> task = Objects.isNull(lifecycle) ? Ddd4jVertxContext.executeBlocking(context, operation)
                : lifecycle.execute(context, operation);
        task.onComplete(result -> context.vertx().runOnContext(ignored -> {
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

    /** 登录令牌响应。 */
    public final static class TokenResponse {

        private static final long serialVersionUID = 0L;

        private final String token;

        /**
 * @param token 登录令牌
 */
        @JsonCreator()
        public TokenResponse(@JsonProperty("token") String token) {
            this.token = token;
        }

        /**
 * @return 登录令牌
 */
        @JsonProperty("token")
        public String token() {
            return token;
        }

        /**
 * @return 登录令牌
 */
        public String getToken() {
            return token;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TokenResponse other = (TokenResponse) obj;
            return Objects.equals(this.token, other.token);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + (Objects.isNull(token) ? 43 : token.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "VertxOrderRoutes.TokenResponse(token=" + token + ")";
        }
    }
}
