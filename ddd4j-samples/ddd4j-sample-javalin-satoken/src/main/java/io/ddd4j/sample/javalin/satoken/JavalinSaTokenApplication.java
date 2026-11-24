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

import com.google.inject.Guice;
import io.ddd4j.guice.Ddd4jGuiceModule;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.ddd.repository.Repository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.javalin.json.JavalinJackson;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.EnumSet;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.subject.SubjectStrategy;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.guice.subject.GuiceSubjectProvider;
import io.ddd4j.sample.javalin.satoken.config.AuthModule;
import io.ddd4j.sample.javalin.satoken.goods.domain.Goods;
import io.ddd4j.sample.javalin.satoken.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.satoken.goods.web.GoodsQueryResource;
import io.ddd4j.sample.javalin.satoken.goods.web.GoodsResource;
import io.ddd4j.sample.javalin.satoken.order.web.OrderResource;
import io.ddd4j.sample.javalin.satoken.rbac.RbacConfig;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.UserRepository;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthenticationController;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthorizationController;
import io.javalin.Javalin;
import io.javalin.apibuilder.ApiBuilder;
import lombok.extern.slf4j.Slf4j;

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
        start(PORT);
    }

    /**
     * @param port HTTP 端口
     * @return 可关闭的真实应用
     */
    public static Application start(int port) {
        return start(port, ignored -> { });
    }

    static Application start(int port, Consumer<Optional<String>> observer) {
        return start(port, observer, config -> { });
    }

    static Application start(int port, Consumer<Optional<String>> observer,
                             Consumer<io.javalin.config.JavalinConfig> configure) {
        // ==================== 1. 创建 Guice 注入器 ====================
        NativeAuthLifecycle lifecycle = new NativeAuthLifecycle(observer);
        try {
            lifecycle.initializeSaToken();
        Module authModule = new AuthModule();
        Injector injector = Guice.createInjector(Ddd4jGuiceModule.basic(), authModule, new com.google.inject.AbstractModule() {
            @Override protected void configure() {
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
        var adapters = new io.ddd4j.sample.order.local.InMemoryOrderAdapters();
        var sharedOrders = new io.ddd4j.sample.order.application.OrderApplicationService(adapters, adapters, adapters, adapters);

        // ==================== 5. 获取 RBAC 控制器与 Resource 实例 ====================
        AuthenticationController authController = injector.getInstance(AuthenticationController.class);
        AuthorizationController authzController = injector.getInstance(AuthorizationController.class);
        OrderResource orderResource = new OrderResource(sharedOrders, adapters);
        GoodsResource goodsResource = injector.getInstance(GoodsResource.class);
        GoodsQueryResource goodsQueryResource = injector.getInstance(GoodsQueryResource.class);

        // ==================== 6. 启动 Javalin 并注册路由 ====================
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.routes.before(lifecycle::authenticateOrder);
            configure.accept(javalinConfig);
            javalinConfig.jsonMapper(new JavalinJackson(JsonMapper.builder().findAndAddModules().build(), false));
            javalinConfig.jetty.modifyServletContextHandler(context -> context.addFilter(
                    new FilterHolder(lifecycle), "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC)));
            javalinConfig.routes.exception(Exception.class, (failure, ctx) -> ctx.status(500).json(
                    io.ddd4j.core.api.R.of(500, failure.getMessage())));
            javalinConfig.routes.beforeMatched(lifecycle::authorizeMatchedOrder);
            javalinConfig.routes.apiBuilder(() -> {
                // ========== Authentication 路由（/auth/*） ==========
                authController.routes().addEndpoints();

                // ========== Authorization 路由（/rbac/admin/*，RBAC 管理） ==========
                ApiBuilder.path("rbac", authzController.routes());

                // ========== Order 路由（EndpointGroup） ==========
                orderResource.routes().addEndpoints();

                // ========== Goods 路由（写侧 + 读侧） ==========
                goodsQueryResource.routes().addEndpoints();
                goodsResource.routes().addEndpoints();
            });
        });

        lifecycle.attach(app);
        app.start(port);
        log.info("Javalin + Sa-Token + RBAC + DDD sample started at http://localhost:{}", PORT);
        log.info("Authentication endpoints: /auth/login, /auth/me, /auth/admin, /auth/users");
        log.info("Authorization endpoints: /rbac/admin/users, /rbac/admin/roles, /rbac/admin/permissions");
        log.info("Order endpoints: /orders, /orders/{id}, /orders/{id}/pay");
        log.info("Goods endpoints: /api/goods, /api/goods/page, /api/goods/list");
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

    /** 可关闭的实际 Javalin/Guice 应用句柄。 */
    public static final class Application implements AutoCloseable {
        private final NativeAuthLifecycle lifecycle;
        private Application(NativeAuthLifecycle lifecycle) { this.lifecycle = lifecycle; }
        /** @return 实际 HTTP 应用 */
        public Javalin app() { return lifecycle.app(); }
        @Override public void close() { lifecycle.close(); }
    }

}
