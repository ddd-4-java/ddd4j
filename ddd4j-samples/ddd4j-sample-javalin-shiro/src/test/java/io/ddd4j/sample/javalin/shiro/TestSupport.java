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

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.subject.SubjectStrategy;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.javalin.shiro.config.AuthConfig;
import io.ddd4j.sample.javalin.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.shiro.goods.domain.Goods;
import io.ddd4j.sample.javalin.shiro.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.shiro.goods.web.GoodsQueryResource;
import io.ddd4j.sample.javalin.shiro.goods.web.GoodsResource;
import io.ddd4j.sample.javalin.shiro.rbac.RbacConfig;
import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthenticationController;
import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthorizationController;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import io.javalin.Javalin;
import io.javalin.apibuilder.ApiBuilder;
import io.javalin.json.JavalinJackson;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.util.Objects;

/**
 * 测试基础设施：手动构造 Shiro SecurityManager + Javalin 应用。
 *
 * <p>本 sample 与 satoken 不同的关键点：Shiro 是线程级 Subject，
 * 通过 {@link ThreadContext#bind(Subject)} 绑定当前线程 Subject。
 * 在 HTTP 请求进入时绑定、请求结束后清理即可让 Shiro 工作。
 */
public final class TestSupport {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>TestSupport</code> \u5b9e\u4f8b\u3002 */
    private TestSupport() {
    }

    /**
     * \u8bfb\u53d6 <code>app</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>app</code>
     */
    public static Javalin start() {
        // 0) 注入 SPI
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, new NoopDomainEventPublisher());
        BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, new NoopI18nProvider());

        // 1) 手动构造 RBAC + Shiro SecurityManager
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryRoleRepository roleRepository = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissionRepository = new InMemoryPermissionRepository();
        RbacService rbacService = new RbacService(userRepository, roleRepository, permissionRepository);
        RbacConfig.initSeedData(rbacService);

        AuthConfig.initShiro(userRepository, roleRepository, rbacService);

        // 2) SubjectKit SPI — 测试专用 WorkingSubjectProvider 绕开上游 login bug
        SubjectProvider subjectProvider = new WorkingSubjectProvider(userRepository);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider);
        SubjectKit.register(subjectProvider);

        SubjectDataProvider subjectDataProvider = RbacConfig.createSubjectDataProvider(userRepository, roleRepository);
        SubjectKit.setDataProvider(subjectDataProvider);

        SubjectKit.setStrategy(SubjectStrategy.instance);

        // 3) Goods
        InMemoryGoodsRepository goodsRepository = new InMemoryGoodsRepository();
        RepositoryRegistry.register(Goods.class, goodsRepository);
        GoodsApplicationService goodsApp = new GoodsApplicationService(goodsRepository);
        GoodsResource goodsResource = new GoodsResource(goodsApp);
        GoodsQueryResource goodsQueryResource = new GoodsQueryResource(goodsApp);


        // 5) RBAC 控制器
        AuthenticationController authController = new AuthenticationController(rbacService);
        AuthorizationController authzController = new AuthorizationController(rbacService);

        // 6) 启动 Javalin
        Javalin app = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.jsonMapper(new JavalinJackson());

            // Shiro 是线程级 Subject；
            // 每个请求进入前，根据请求头中的 token（即 sessionId）从 SessionManager 取出会话，
            // 再用 Subject.Builder 构造带 principal 的 Subject 并绑定到 ThreadContext，
            // 让 SecurityUtils.getSubject() 能取回登录态。
            cfg.routes.before(ctx -> {
                ThreadContext.remove();
                String token = ctx.header("Authorization");
                if (Objects.nonNull(token) && token.startsWith("Bearer ")) {
                    String sessionId = token.substring("Bearer ".length()).trim();
                    try {
                        org.apache.shiro.session.mgt.SessionKey key =
                                new org.apache.shiro.session.mgt.DefaultSessionKey(sessionId);
                        org.apache.shiro.session.Session session =
                                SecurityUtils.getSecurityManager().getSession(key);
                        if (Objects.nonNull(session)) {
                            Object user = session.getAttribute("user");
                            org.apache.shiro.subject.PrincipalCollection principals =
                                    Objects.nonNull(user)
                                            ? new org.apache.shiro.subject.SimplePrincipalCollection(user, "rbacRealm")
                                            : null;
                            org.apache.shiro.subject.Subject shiroSubject =
                                    new org.apache.shiro.subject.Subject.Builder(SecurityUtils.getSecurityManager())
                                            .session(session)
                                            .principals(principals)
                                            .authenticated(Objects.nonNull(principals))
                                            .sessionCreationEnabled(false)
                                            .buildSubject();
                            ThreadContext.bind(shiroSubject);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            cfg.routes.after(ctx -> ThreadContext.remove());

            cfg.routes.apiBuilder(() -> {
                // Authentication
                ApiBuilder.post("/auth/login", authController::login);
                ApiBuilder.post("/auth/logout", authController::logout);
                ApiBuilder.get("/auth/me", authController::me);
                ApiBuilder.get("/auth/check/permission", authController::checkPermission);
                ApiBuilder.get("/auth/check/role", authController::checkRole);
                ApiBuilder.post("/auth/kickout", authController::kickout);
                ApiBuilder.get("/auth/status", authController::status);

                // Authorization
                ApiBuilder.get("/auth/users", authzController::listUsers);
                ApiBuilder.get("/auth/users/{id}", authzController::getUser);
                ApiBuilder.post("/auth/users", authzController::createUser);
                ApiBuilder.put("/auth/users/{id}", authzController::updateUser);
                ApiBuilder.delete("/auth/users/{id}", authzController::deleteUser);
                ApiBuilder.get("/auth/roles", authzController::listRoles);
                ApiBuilder.post("/auth/roles", authzController::createRole);
                ApiBuilder.put("/auth/roles/{code}", authzController::updateRole);
                ApiBuilder.delete("/auth/roles/{code}", authzController::deleteRole);
                ApiBuilder.get("/auth/permissions", authzController::listPermissions);
                ApiBuilder.post("/auth/permissions", authzController::createPermission);
                ApiBuilder.delete("/auth/permissions/{code}", authzController::deletePermission);

                // Business auth
                ApiBuilder.post("/auth/orders/{id}/pay", c -> {
                    if (!SubjectKit.hasPermission("order:pay")) {
                        c.status(403).json(io.ddd4j.core.api.R.of(403, "forbidden: requires order:pay"));
                        return;
                    }
                    String id = c.pathParam("id");
                    c.json(io.ddd4j.core.api.R.ok(java.util.Map.of(
                            "orderId", id,
                            "byUser", String.valueOf(SubjectKit.getLoginId()))));
                });

                goodsQueryResource.routes().addEndpoints();
                goodsResource.routes().addEndpoints();
            });
        });

        // 启动前清理任何残留 Shiro 状态
        ThreadContext.remove();
        try {
            SecurityManager sm = SecurityUtils.getSecurityManager();
            if (Objects.nonNull(sm)) {
                // 清理：unbind security manager 不需要；只清理线程 Subject
                ThreadContext.remove();
            }
        } catch (Exception ignored) {
        }

        app.start(0);
        return app;
    }

    /** \u628a\u9886\u57df\u4e8b\u4ef6\u4ea4\u7ed9\u53ef\u68c0\u67e5\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    private static final class NoopDomainEventPublisher implements DomainEventPublisher {
        /**
         * 提供测试替身回调，调用时保持夹具状态。
         *
         * @param <ID> 实体标识类型
         * @param event 领域事件
         */
        @Override
        public <ID extends EntityId> void publish(io.ddd4j.core.ddd.event.DomainEvent<ID> event) {
        }
    }

    /** \u8fd4\u56de\u7528\u4e8e\u6821\u9a8c\u672c\u5730\u5316\u5165\u53e3\u7684\u56fa\u5b9a\u6d88\u606f\u3002 */
    private static final class NoopI18nProvider implements I18nProvider {
        /**
         * 按提供的条件读取或解析消息文本。
         *
         * @param key 消息 key
         * @param args 格式化参数
         * @return 实际返回<code>key</code>，供调用方取得当前值
         */
        @Override
        public String getMessage(String key, Object... args) {
            return key;
        }
    }
}
