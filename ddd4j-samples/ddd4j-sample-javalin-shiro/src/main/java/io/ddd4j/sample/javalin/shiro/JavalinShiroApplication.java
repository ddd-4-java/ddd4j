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
package io.ddd4j.sample.javalin.shiro;

import com.google.common.collect.ImmutableMap;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.ddd4j.core.api.R;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.guice.Ddd4jGuiceModule;
import io.ddd4j.sample.javalin.shiro.config.AuthConfig;
import io.ddd4j.sample.javalin.shiro.config.AuthModule;
import io.ddd4j.sample.javalin.shiro.goods.domain.Goods;
import io.ddd4j.sample.javalin.shiro.rbac.RbacConfig;
import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthenticationController;
import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthorizationController;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.javalin.Javalin;
import io.javalin.apibuilder.ApiBuilder;
import io.javalin.plugin.json.JavalinJackson;
import javax.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.servlet.FilterHolder;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Javalin + Guice + Apache Shiro + RBAC 鉴权 + DDD 业务示例启动类。
 *
 * <h2>核心要点</h2>
 * <p>Javalin 本身<b>没有 DI 容器</b>，使用 Google Guice 注入业务服务。
 * 同时，ddd4j 的 4 个核心 SPI（DomainEventPublisher / MQEventPublisher /
 * SubjectProvider / I18nProvider）需要手动注册到 {@link BaseContext} 才能被
 * 业务代码通过 {@link io.ddd4j.core.context.Contexts#getOrThrow(String, Class)} 查找到。
 *
 * <h3>Shiro 适配的特殊性</h3>
 * <p>Shiro 不同于 Sa-Token，它依赖一个<b>JVM 级 SecurityManager</b>（通过
 * {@code SecurityUtils.setSecurityManager(...)} 设置）。所以启动流程比 Sa-Token
 * 多一步：先调用 {@link AuthConfig#initShiro} 完成 Shiro 自举。
 *
 * <h3>RBAC 启动顺序</h3>
 * <ol>
 *   <li>构造 RBAC 仓储（User/Role/Permission）</li>
 *   <li>初始化种子数据（admin/zhangsan/lisi）</li>
 *   <li>构造 RbacService 与 Shiro Realm，初始化 SecurityManager</li>
 *   <li>把 RBAC 仓储传入 AuthModule，让 Guice 与 Shiro 共享同一份数据</li>
 * </ol>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class JavalinShiroApplication {

    /**
     * Javalin 监听端口
     */
    public static final int PORT = 7000;

    /**
     * 启动 Javalin Shiro 示例。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        start(Integer.getInteger("sample.port", PORT));
    }

    /**
     * @param port HTTP 端口
     * @return 可关闭的真实应用
     */
    public static Application start(int port) {
        return start(port, ignored -> {
        }, config -> {
        }, CatalogRuntime.start());
    }

    /**
     * 执行 <code>start</code>，调用 <code>RbacConfig.initSeedData</code>、<code>mgt.DefaultSecurityManager</code>、<code>AuthConfig.RbacRealm</code>。
     *
     * @param port 传入此操作的 <code>int</code> 类型 <code>port</code>
     * @param observer 传入此操作的 <code>Consumer&lt;Optional&lt;String&gt;&gt;</code> 类型 <code>observer</code>
     * @param configure 传入此操作的 <code>Consumer&lt;io.javalin.Javalin&gt;</code> 类型 <code>configure</code>
     * @param catalog 传入此操作的 <code>CatalogEndpoints</code> 类型 <code>catalog</code>
     * @return 当前操作得到的 <code>Application</code> 结果
     */
    static Application start(int port, Consumer<Optional<String>> observer,
                             Consumer<io.javalin.Javalin> configure, CatalogEndpoints catalog) {
        // ==================== 1. 初始化 RBAC + Shiro SecurityManager ====================
        // 先构造 RBAC 仓储和种子数据（Shiro 与 Guice 共享同一份数据）
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryRoleRepository roleRepository = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissionRepository = new InMemoryPermissionRepository();
        RbacService rbacService = new RbacService(userRepository, roleRepository, permissionRepository);
        RbacConfig.initSeedData(rbacService);

        // 初始化 Shiro SecurityManager（基于 RBAC Realm）
        NativeAuthLifecycle lifecycle = new NativeAuthLifecycle(new org.apache.shiro.mgt.DefaultSecurityManager(
                new AuthConfig.RbacRealm(userRepository, roleRepository, rbacService)), observer);
        try {

            // ==================== 2. 创建 Guice 注入器（共享 RBAC 仓储）====================
            Module authModule = new AuthModule(userRepository, roleRepository, permissionRepository, rbacService);
            Injector injector = Guice.createInjector(Ddd4jGuiceModule.basic(), authModule, new com.google.inject.AbstractModule() {
                @Override
                protected void configure() {
                    bind(Subject.class).toInstance(new io.ddd4j.auth.shiro.subject.ShiroSubject());
                }
            });
            lifecycle.attach(injector);

            lifecycle.repositories.register(RepositoryRegistry.key(Goods.class), Repository.class,
                    injector.getInstance(io.ddd4j.sample.javalin.shiro.goods.domain.GoodsRepository.class));
            lifecycle.repositories.start();

            // ==================== 4. 获取 RBAC 控制器与 Resource 实例 ====================
            AuthenticationController authController = injector.getInstance(AuthenticationController.class);
            AuthorizationController authzController = injector.getInstance(AuthorizationController.class);

            // ==================== 5. 启动 Javalin 并注册路由 ====================
            Javalin app = Javalin.create(javalinConfig -> {
                javalinConfig.showJavalinBanner = false;
                javalinConfig.jsonMapper(new JavalinJackson(JsonMapper.builder().findAndAddModules().build()));
                javalinConfig.configureServletContextHandler(context -> context.addFilter(
                        new FilterHolder(lifecycle), "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC)));
            });
            configure.accept(app);
            app.exception(Exception.class, (failure, ctx) -> ctx.status(500).json(
                        io.ddd4j.core.api.R.of(500, failure.getMessage())));
            app.routes(() -> {
                    ApiBuilder.post("/orders/catalog/goods/{code}", ctx -> {
                        if (!lifecycle.authorizeOrder(ctx, "order:pay")) {
                            return;
                        }
                        io.ddd4j.sample.order.adapter.http.Reply reply = catalog.createGoods(ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body());
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.get("/orders/catalog/goods/{code}", ctx -> {
                        if (!lifecycle.authorizeOrder(ctx, "order:read")) {
                            return;
                        }
                        io.ddd4j.sample.order.adapter.http.Reply reply = catalog.goods(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.post("/orders/catalog/orders/{code}", ctx -> {
                        if (!lifecycle.authorizeOrder(ctx, "order:pay")) {
                            return;
                        }
                        io.ddd4j.sample.order.adapter.http.Reply reply = catalog.placeOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body());
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.post("/orders/catalog/orders/{orderNo}/payment", ctx -> {
                        if (!lifecycle.authorizeOrder(ctx, "order:pay")) {
                            return;
                        }
                        io.ddd4j.sample.order.adapter.http.Reply reply = catalog.confirmPayment(ctx.header("Idempotency-Key"), ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"), ctx.body());
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.post("/orders/catalog/orders/{orderNo}/cancel", ctx -> {
                        if (!lifecycle.authorizeOrder(ctx, "order:cancel")) {
                            return;
                        }
                        io.ddd4j.sample.order.adapter.http.Reply reply = catalog.cancelOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"));
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.post("/orders/catalog/orders/{orderNo}/fulfill", ctx -> {
                        if (!lifecycle.authorizeOrder(ctx, "order:pay")) {
                            return;
                        }
                        io.ddd4j.sample.order.adapter.http.Reply reply = catalog.fulfillOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"));
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    ApiBuilder.get("/orders/catalog/orders/{code}", ctx -> {
                        if (!lifecycle.authorizeOrder(ctx, "order:read")) {
                            return;
                        }
                        io.ddd4j.sample.order.adapter.http.Reply reply = catalog.order(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
                        ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
                    });
                    // ========== Authentication 路由 ==========
                    ApiBuilder.post("/auth/login", authController::login);
                    ApiBuilder.post("/auth/logout", authController::logout);
                    ApiBuilder.get("/auth/me", authController::me);
                    ApiBuilder.get("/auth/check/permission", authController::checkPermission);
                    ApiBuilder.get("/auth/check/role", authController::checkRole);
                    ApiBuilder.post("/auth/kickout", authController::kickout);
                    ApiBuilder.get("/auth/status", authController::status);

                    // ========== Authorization 路由（RBAC 管理） ==========
                    // User CRUD
                    ApiBuilder.get("/auth/users", authzController::listUsers);
                    ApiBuilder.get("/auth/users/{id}", authzController::getUser);
                    ApiBuilder.post("/auth/users", authzController::createUser);
                    ApiBuilder.put("/auth/users/{id}", authzController::updateUser);
                    ApiBuilder.delete("/auth/users/{id}", authzController::deleteUser);

                    // Role CRUD
                    ApiBuilder.get("/auth/roles", authzController::listRoles);
                    ApiBuilder.post("/auth/roles", authzController::createRole);
                    ApiBuilder.put("/auth/roles/{code}", authzController::updateRole);
                    ApiBuilder.delete("/auth/roles/{code}", authzController::deleteRole);

                    // Permission CRUD
                    ApiBuilder.get("/auth/permissions", authzController::listPermissions);
                    ApiBuilder.post("/auth/permissions", authzController::createPermission);
                    ApiBuilder.delete("/auth/permissions/{code}", authzController::deletePermission);

                    // ========== 业务接口鉴权示范 ==========
                    // POST /auth/orders/{id}/pay —— 业务接口 + 鉴权（order:pay 权限）
                    ApiBuilder.post("/auth/orders/{id}/pay", ctx -> {
                        if (!SubjectKit.hasPermission("order:pay")) {
                            ctx.status(403).json(R.of(403, "forbidden: requires order:pay permission"));
                            return;
                        }
                        String id = ctx.pathParam("id");
                        ctx.json(R.ok("order pay authorized", ImmutableMap.of(
                                "orderId", id,
                                "byUser", String.valueOf(SubjectKit.getLoginId()))));
                    });

                    // Order 路由（通过 EndpointGroup 暴露）

            });

            lifecycle.attach(app);
            app.start(port);
            log.info("Javalin + Shiro + RBAC 鉴权 + DDD 业务示例启动于 http://localhost:{}", PORT);
            return new Application(lifecycle);
        } catch (RuntimeException | Error failure) {
            try {
                lifecycle.close();
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) {
                    failure.addSuppressed(cleanup);
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

        /**
         * @return 实际 HTTP 应用
         */
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

    // ==================== 示例 SPI 实现（仅本示例使用） ====================

    /**
     * 空操作的领域事件发布器：仅记录事件日志，不做真实投递（示例用）。
     *
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     */
    private static class NoOpDomainEventPublisher implements DomainEventPublisher {
        /**
         * 发布数据。
         *
         * @param <ID>  类型参数。
         * @param event 事件消息。
         */
        @Override
        public <ID extends io.ddd4j.core.ddd.event.EntityId> void publish(
                io.ddd4j.core.ddd.event.DomainEvent<ID> event) {
            if (Objects.isNull(event)) {
                return;
            }
            log.info("[DomainEvent] {}", event.getClass().getSimpleName());
        }

        /**
         * publishAll() 方法。
         *
         * @param <ID>   类型参数。
         * @param events 领域事件集合。
         */
        @Override
        public <ID extends io.ddd4j.core.ddd.event.EntityId> void publishAll(
                java.util.Collection<io.ddd4j.core.ddd.event.DomainEvent<ID>> events) {
            if (Objects.nonNull(events)) {
                events.forEach(this::publish);
            }
        }
    }

    /**
     * 默认国际化提供者：委托给内置默认实现解析消息键。
     *
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     */
    private static class DefaultI18nProvider implements I18nProvider {
        /**
         * 获取值。
         *
         * @param key  幂等键。
         * @param args 命令行参数。
         * @return 结果字符串。
         */
        @Override
        public String getMessage(String key, Object... args) {
            return I18nProvider.DEFAULT.getMessage(key, args);
        }
    }

}
