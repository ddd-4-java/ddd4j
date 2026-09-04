package io.ddd4j.sample.javalin.shiro;

import java.util.Objects;

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
import io.ddd4j.sample.javalin.shiro.order.application.OrderApplicationService;
import io.ddd4j.sample.javalin.shiro.order.domain.service.OrderDomainService;
import io.ddd4j.sample.javalin.shiro.order.infrastructure.InMemoryOrderRepository;
import io.ddd4j.sample.javalin.shiro.order.web.OrderResource;
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

/**
 * 测试基础设施：手动构造 Shiro SecurityManager + Javalin 应用。
 *
 * <p>本 sample 与 satoken 不同的关键点：Shiro 是线程级 Subject，
 * 通过 {@link ThreadContext#bind(Subject)} 绑定当前线程 Subject。
 * 在 HTTP 请求进入时绑定、请求结束后清理即可让 Shiro 工作。
 */
public final class TestSupport {

    private TestSupport() {
    }

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

        // 4) Order
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        OrderDomainService orderDomainService = new OrderDomainService(orderRepository);
        OrderApplicationService orderApp = new OrderApplicationService(orderRepository, orderDomainService);
        OrderResource orderResource = new OrderResource(orderApp);

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
                        c.status(403).json(io.ddd4j.core.api.R.fail(403, "forbidden: requires order:pay"));
                        return;
                    }
                    String id = c.pathParam("id");
                    c.json(io.ddd4j.core.api.R.ok(java.util.Map.of(
                            "orderId", id,
                            "byUser", String.valueOf(SubjectKit.getLoginId()))));
                });

                orderResource.routes().addEndpoints();
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

    private static final class NoopDomainEventPublisher implements DomainEventPublisher {
        @Override
        public <ID extends EntityId> void publish(io.ddd4j.core.ddd.event.DomainEvent<ID> event) {
        }
    }

    private static final class NoopI18nProvider implements I18nProvider {
        @Override
        public String getMessage(String key, Object... args) {
            return key;
        }
    }
}
