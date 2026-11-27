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
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Vert.x 商品交易 HTTP 适配层。
 */
public final class VertxOrderRoutes {

    /** \u5546\u54c1\u4ea4\u6613\u54cd\u5e94\u58f0\u660e\u7684 JSON \u5a92\u4f53\u7c7b\u578b\u3002 */
    private static final String JSON_CONTENT_TYPE = "application/json";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>VertxOrderRoutes</code> \u5b9e\u4f8b\u3002 */
    private VertxOrderRoutes() {
    }

    /**
     * \u4e3a\u6307\u5b9a\u8ba4\u8bc1\u6a21\u5f0f\u88c5\u914d\u5546\u54c1\u4ea4\u6613\u8def\u7531\u3002
     * @param vertx \u5f53\u524d\u5e94\u7528\u7684 Vert.x \u5b9e\u4f8b
     * @param catalog \u5df2\u88c5\u914d\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c
     * @param mode HTTP \u8def\u7531\u4f7f\u7528\u7684\u8ba4\u8bc1\u6a21\u5f0f
     * @return \u914d\u7f6e\u597d\u5546\u54c1\u4ea4\u6613\u8def\u7531\u7684 Router
     */
    public static Router router(Vertx vertx, CatalogEndpoints catalog, AuthenticationMode mode) {
        return router(vertx, catalog, mode, null);
    }

    /**
     * \u7ec4\u88c5\u6307\u5b9a\u8ba4\u8bc1\u6a21\u5f0f\u4e0b\u7684 Vert.x \u5546\u54c1\u4ea4\u6613\u8def\u7531\u3002
     * @param vertx \u62e5\u6709\u5f53\u524d HTTP \u670d\u52a1\u5668\u7684 Vert.x \u5b9e\u4f8b
     * @param catalog \u5f53\u524d\u5546\u54c1\u4ea4\u6613\u5165\u53e3
     * @param mode \u5f53\u524d\u5546\u54c1\u4ea4\u6613\u8def\u7531\u542f\u7528\u7684\u8ba4\u8bc1\u6a21\u5f0f
     * @param lifecycle \u7531\u5f53\u524d\u5e94\u7528\u6301\u6709\u5e76\u5728\u5173\u95ed\u65f6\u56de\u6536\u7684\u8ba4\u8bc1\u751f\u547d\u5468\u671f
     * @return \u5df2\u7ecf\u88c5\u914d\u5f53\u524d\u8bbf\u95ee\u7b56\u7565\u7684\u8def\u7531\u5668
     */
    static Router router(Vertx vertx, CatalogEndpoints catalog, AuthenticationMode mode,
                         VertxOrderApplication lifecycle) {
        Vertx actualVertx = Objects.requireNonNull(vertx, "vertx must not be null");
        Router router = Router.router(actualVertx);
        // Vert.x requires body parsing to be the first global handler.
        router.route().handler(BodyHandler.create());
        new Ddd4jVertxWeb(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/api/auth/**"),
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

    /**
     * \u5728\u5e94\u7528\u751f\u547d\u5468\u671f\u5185\u8c03\u5ea6\u5f53\u524d HTTP \u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u3002
     * @param lifecycle \u7531\u5f53\u524d\u5e94\u7528\u6301\u6709\u5e76\u5728\u5173\u95ed\u65f6\u56de\u6536\u7684\u8ba4\u8bc1\u751f\u547d\u5468\u671f
     * @param ctx \u5f53\u524d Web \u6846\u67b6\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param call \u5f85\u6267\u884c\u7684\u4ea4\u6613\u8c03\u7528
     */
    private static void trade(VertxOrderApplication lifecycle, RoutingContext ctx, Callable<Reply> call) {
        var task = Objects.isNull(lifecycle) ? Ddd4jVertxContext.executeBlocking(ctx, call)
                : lifecycle.execute(ctx, call);
        task.onSuccess(reply -> ctx.response().setStatusCode(reply.getStatus())
                .putHeader("Content-Type", JSON_CONTENT_TYPE).end(reply.getBody())).onFailure(ctx::fail);
    }

    /**
     * \u7531\u5f53\u524d\u5e94\u7528\u8fd0\u884c\u6240\u63a5\u6536\u7684\u4e1a\u52a1\u64cd\u4f5c\u5e76\u53d1\u9001\u54cd\u5e94\u3002
     * @param <T> \u5f53\u524d\u793a\u4f8b\u6240\u4f7f\u7528\u7684\u6570\u636e\u6216\u6846\u67b6\u7c7b\u578b
     * @param lifecycle \u7531\u5f53\u524d\u5e94\u7528\u6301\u6709\u5e76\u5728\u5173\u95ed\u65f6\u56de\u6536\u7684\u8ba4\u8bc1\u751f\u547d\u5468\u671f
     * @param context \u5f53\u524d Vert.x \u8def\u7531\u4e0a\u4e0b\u6587
     * @param operation \u5f53\u524d\u8bf7\u6c42\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u6216\u5f85\u6267\u884c\u7684\u5de5\u4f5c\u56de\u8c03
     * @param status \u5546\u54c1\u72b6\u6001\u6761\u4ef6\u6587\u672c
     */
    private static <T> void execute(VertxOrderApplication lifecycle, RoutingContext context, Callable<T> operation, int status) {
        var task = Objects.isNull(lifecycle) ? Ddd4jVertxContext.executeBlocking(context, operation)
                : lifecycle.execute(context, operation);
        task.onComplete(result -> context.vertx().runOnContext(ignored -> {
                    if (result.failed()) {
                        context.fail(result.cause());
                        return;
                    }
                    respond(context, status, result.result());
                }));
    }

    /**
     * \u5c06\u5df2\u786e\u5b9a\u7684\u72b6\u6001\u548c\u6b63\u6587\u5199\u5165\u5f53\u524d Vert.x \u54cd\u5e94\u3002
     * @param context \u5f53\u524d Vert.x \u8def\u7531\u4e0a\u4e0b\u6587
     * @param status \u5546\u54c1\u72b6\u6001\u6761\u4ef6\u6587\u672c
     * @param response \u5f53\u524d\u539f\u751f HTTP \u54cd\u5e94\u6216\u54cd\u5e94\u6784\u5efa\u5668
     */
    private static void respond(RoutingContext context, int status, Object response) {
        context.response().setStatusCode(status).putHeader("Content-Type", JSON_CONTENT_TYPE)
                .end(Json.encode(response));
    }

    /** 登录令牌响应。 */
    public final static class TokenResponse {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u5f53\u524d\u793a\u4f8b\u7b7e\u53d1\u6216\u8fd4\u56de\u5ba2\u6237\u7aef\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002 */
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

        /**
         * 根据<code>token</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
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

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + (Objects.isNull(token) ? 43 : token.hashCode());
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>token</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "VertxOrderRoutes.TokenResponse(token=" + token + ")";
        }
    }
}
