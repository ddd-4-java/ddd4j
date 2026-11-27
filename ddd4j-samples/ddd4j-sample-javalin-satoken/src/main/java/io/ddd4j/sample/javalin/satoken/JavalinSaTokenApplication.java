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
package io.ddd4j.sample.javalin.satoken;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.guice.Ddd4jGuiceModule;
import io.ddd4j.sample.javalin.satoken.config.AuthModule;
import io.ddd4j.sample.javalin.satoken.goods.domain.Goods;
import io.ddd4j.sample.javalin.satoken.rbac.RbacConfig;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.UserRepository;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthenticationController;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthorizationController;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.javalin.Javalin;
import io.javalin.apibuilder.ApiBuilder;
import io.javalin.json.JavalinJackson;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee10.servlet.FilterHolder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Javalin + Guice + Sa-Token + RBAC 鉴权 + DDD 业务示例启动类。
 *
 * <h2>核心要点</h2>
 * <p>Javalin 本身<b>没有 DI 容器</b>，使用 Google Guice 注入业务服务。
 * 业务代码（RBAC User/Role/Permission/Repository/Service）与 Spring 示例完全一致，
 * 仅 Controller 层使用 Javalin {@link ApiBuilder} 风格。
 *
 * <h3>RBAC 演示要点</h3>
 * <ul>
 *   <li>完整 User / Role / Permission 内存 CRUD（{@link AuthorizationController}）</li>
 *   <li>登录 / 登出 / 当前用户 / 权限角色校验（{@link AuthenticationController}）</li>
 *   <li>业务接口鉴权示范：{@code GET /auth/users} 需要 user:list 权限；{@code POST /auth/orders/{id}/pay} 需要 order:pay 权限；{@code DELETE /auth/users/{id}} 需要 admin 角色 + user:delete 权限（组合鉴权）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class JavalinSaTokenApplication {

    /**
     * Javalin 监听端口
     */
    public static final int PORT = 8085;

    /**
     * 启动 Javalin Sa-Token 示例。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        start(Integer.getInteger("sample.port", PORT));
    }

    /**
     * 执行 <code>start</code>，调用 <code>CatalogRuntime.start</code>。
     *
     * @param port HTTP \u7aef\u53e3
     * @return \u53ef\u5173\u95ed\u7684\u771f\u5b9e\u5e94\u7528
     */
    public static Application start(int port) {
        return start(port, ignored -> {
        }, config -> {
        }, CatalogRuntime.start());
    }

    /**
     * \u7ec4\u88c5\u539f\u751f\u8ba4\u8bc1\u8fc7\u6ee4\u5668\u548c\u5546\u54c1\u4ea4\u6613\u8def\u7531\uff0c\u7136\u540e\u542f\u52a8 Javalin\u3002
     * @param port \u5f53\u524d HTTP \u793a\u4f8b\u8981\u76d1\u542c\u7684\u7aef\u53e3
     * @param observer \u89c2\u5bdf\u8bf7\u6c42\u7ed3\u675f\u540e\u539f\u751f\u8ba4\u8bc1\u4e0a\u4e0b\u6587\u7684\u56de\u8c03
     * @param configure \u8c03\u6574 Javalin \u542f\u52a8\u914d\u7f6e\u7684\u56de\u8c03
     * @param catalog \u5f53\u524d\u5546\u54c1\u4ea4\u6613\u5165\u53e3
     * @return \u5df2\u542f\u52a8\u7684 Javalin \u8ba4\u8bc1\u793a\u4f8b
     */
    static Application start(int port, Consumer<Optional<String>> observer,
                             Consumer<io.javalin.config.JavalinConfig> configure, CatalogEndpoints catalog) {
        // ==================== 1. 创建 Guice 注入器 ====================
        NativeAuthLifecycle lifecycle = new NativeAuthLifecycle(observer);
        try {
            lifecycle.initializeSaToken();
            Module authModule = new AuthModule();
            Injector injector = Guice.createInjector(Ddd4jGuiceModule.basic(), authModule, new com.google.inject.AbstractModule() {
                @Override
                protected void configure() {
                    bind(Subject.class).toInstance(new io.ddd4j.auth.satoken.subject.SaTokenSubject());
                }
            });
            lifecycle.attach(injector);

            // ==================== 2. 预置 RBAC 演示数据 ====================
            RbacConfig.initRbacData(
                    injector.getInstance(UserRepository.class),
                    injector.getInstance(RoleRepository.class),
                    injector.getInstance(PermissionRepository.class));

            lifecycle.repositories.register(RepositoryRegistry.key(Goods.class), Repository.class,
                    injector.getInstance(io.ddd4j.sample.javalin.satoken.goods.domain.GoodsRepository.class));
            lifecycle.repositories.start();

            // ==================== 5. 获取 RBAC 控制器与 Resource 实例 ====================
            AuthenticationController authController = injector.getInstance(AuthenticationController.class);
            AuthorizationController authzController = injector.getInstance(AuthorizationController.class);

            // ==================== 6. 启动 Javalin 并注册路由 ====================
            Javalin app = Javalin.create(javalinConfig -> {
                javalinConfig.startup.showJavalinBanner = false;
                javalinConfig.routes.before(lifecycle::authenticateOrder);
                configure.accept(javalinConfig);
                javalinConfig.jsonMapper(new JavalinJackson(JsonMapper.builder().findAndAddModules().build(), false));
                javalinConfig.jetty.modifyServletContextHandler(context -> context.addFilter(
                        new FilterHolder(lifecycle), "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC)));
                javalinConfig.routes.exception(Exception.class, (failure, ctx) -> ctx.status(500).json(
                        io.ddd4j.core.api.R.fail(500, failure.getMessage())));
                javalinConfig.routes.beforeMatched(lifecycle::authorizeMatchedOrder);
                javalinConfig.routes.apiBuilder(() -> {
                    ApiBuilder.post("/orders/catalog/goods/{code}", ctx -> {
                        var reply = catalog.createGoods(ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body());
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.get("/orders/catalog/goods/{code}", ctx -> {
                        var reply = catalog.goods(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.post("/orders/catalog/orders/{code}", ctx -> {
                        var reply = catalog.placeOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body());
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.post("/orders/catalog/orders/{orderNo}/payment", ctx -> {
                        var reply = catalog.confirmPayment(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"), ctx.body());
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.post("/orders/catalog/orders/{orderNo}/cancel", ctx -> {
                        var reply = catalog.cancelOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"));
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.post("/orders/catalog/orders/{orderNo}/fulfill", ctx -> {
                        var reply = catalog.fulfillOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"));
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.get("/orders/catalog/orders/{code}", ctx -> {
                        var reply = catalog.order(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    // ========== Authentication 路由（/auth/*） ==========
                    authController.routes().addEndpoints();

                    // ========== Authorization 路由（/rbac/admin/*，RBAC 管理） ==========
                    ApiBuilder.path("rbac", authzController.routes());

                    // ========== Order 路由（EndpointGroup） ==========

                });
            });

            lifecycle.attach(app);
            app.start(port);
            log.info("Javalin + Sa-Token + RBAC + DDD sample started at http://localhost:{}", PORT);
            log.info("Authentication endpoints: /auth/login, /auth/me, /auth/admin, /auth/users");
            log.info("Authorization endpoints: /rbac/admin/users, /rbac/admin/roles, /rbac/admin/permissions");
            log.info("Order endpoints: /orders/catalog/orders/{orderNo}");
            log.info("Goods endpoints: /orders/catalog/goods/{spuCode}");
            log.info("Authorization examples: GET /auth/users, POST /auth/orders/{id}/pay, DELETE /auth/users/{id}");
            return new Application(lifecycle);
        } catch (RuntimeException | Error failure) {
            try {
                lifecycle.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * 可关闭的实际 Javalin/Guice 应用句柄。
     */
    public static final class Application implements AutoCloseable {
        /** \u5f53\u524d\u793a\u4f8b\u62e5\u6709\u548c\u5173\u95ed\u539f\u751f\u8ba4\u8bc1\u8d44\u6e90\u7684\u751f\u547d\u5468\u671f\u7ec4\u4ef6\u3002 */
        private final NativeAuthLifecycle lifecycle;

        /**
         * \u628a\u8ba4\u8bc1\u751f\u547d\u5468\u671f\u5173\u8054\u5230\u53ef\u505c\u6b62\u7684 Javalin \u5e94\u7528\u5305\u88c5\u5668\u3002
         * @param lifecycle \u7531\u5f53\u524d\u5e94\u7528\u6301\u6709\u5e76\u5728\u5173\u95ed\u65f6\u56de\u6536\u7684\u8ba4\u8bc1\u751f\u547d\u5468\u671f
         */
        private Application(NativeAuthLifecycle lifecycle) {
            this.lifecycle = lifecycle;
        }

        /** @return \u5b9e\u9645 HTTP \u5e94\u7528 */
        public Javalin app() {
            return lifecycle.app();
        }

        /**
         * 结束当前组件持有的资源。
         *
         * <p>此实现调用 <code>lifecycle.close()</code>。</p>
         */
        @Override
        public void close() {
            lifecycle.close();
        }
    }

}
