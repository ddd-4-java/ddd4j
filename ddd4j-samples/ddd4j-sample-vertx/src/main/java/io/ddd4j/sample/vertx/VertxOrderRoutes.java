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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.vertx.Ddd4jVertxContext;
import io.ddd4j.web.vertx.Ddd4jVertxWeb;
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

    /**
     * 商品交易响应声明的 JSON 媒体类型。
     */
    private static final String JSON_CONTENT_TYPE = "application/json";

    /**
     * 限制调用方直接创建 <code>VertxOrderRoutes</code> 实例。
     */
    private VertxOrderRoutes() {
    }

    /**
     * 为指定认证模式装配商品交易路由。
     *
     * @param vertx   当前应用的 Vert.x 实例
     * @param catalog 已装配的商品交易操作
     * @param mode    HTTP 路由使用的认证模式
     * @return 配置好商品交易路由的 Router
     */
    public static Router router(Vertx vertx, CatalogEndpoints catalog, AuthenticationMode mode) {
        return router(vertx, catalog, mode, null);
    }

    /**
     * 组装指定认证模式下的 Vert.x 商品交易路由。
     *
     * @param vertx     拥有当前 HTTP 服务器的 Vert.x 实例
     * @param catalog   当前商品交易入口
     * @param mode      当前商品交易路由启用的认证模式
     * @param lifecycle 由当前应用持有并在关闭时回收的认证生命周期
     * @return 已经装配当前访问策略的路由器
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
     * 在应用生命周期内调度当前 HTTP 商品交易操作。
     *
     * @param lifecycle 由当前应用持有并在关闭时回收的认证生命周期
     * @param ctx       当前 Web 框架的请求上下文
     * @param call      待执行的交易调用
     */
    private static void trade(VertxOrderApplication lifecycle, RoutingContext ctx, Callable<Reply> call) {
        var task = Objects.isNull(lifecycle) ? Ddd4jVertxContext.executeBlocking(ctx, call)
                : lifecycle.execute(ctx, call);
        task.onSuccess(reply -> ctx.response().setStatusCode(reply.getStatus())
                .putHeader("Content-Type", JSON_CONTENT_TYPE).end(reply.getBody())).onFailure(ctx::fail);
    }

    /**
     * 由当前应用运行所接收的业务操作并发送响应。
     *
     * @param <T>       当前示例所使用的数据或框架类型
     * @param lifecycle 由当前应用持有并在关闭时回收的认证生命周期
     * @param context   当前 Vert.x 路由上下文
     * @param operation 当前请求的商品交易操作或待执行的工作回调
     * @param status    商品状态条件文本
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
     * 将已确定的状态和正文写入当前 Vert.x 响应。
     *
     * @param context  当前 Vert.x 路由上下文
     * @param status   商品状态条件文本
     * @param response 当前原生 HTTP 响应或响应构建器
     */
    private static void respond(RoutingContext context, int status, Object response) {
        context.response().setStatusCode(status).putHeader("Content-Type", JSON_CONTENT_TYPE)
                .end(Json.encode(response));
    }

    /**
     * 登录令牌响应。
     */
    public final static class TokenResponse {

        /**
         * Java 序列化版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 当前示例签发或返回客户端的会话令牌。
         */
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
