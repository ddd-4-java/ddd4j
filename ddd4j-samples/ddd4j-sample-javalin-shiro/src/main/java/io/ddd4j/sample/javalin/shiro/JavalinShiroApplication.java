package io.ddd4j.sample.javalin.shiro;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.ddd4j.auth.shiro.subject.ShiroSubjectProvider;
import io.ddd4j.core.api.R;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.event.MQEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.subject.SubjectStrategy;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.javalin.shiro.config.AuthConfig;
import io.ddd4j.sample.javalin.shiro.config.AuthModule;
import io.ddd4j.sample.javalin.shiro.goods.domain.Goods;
import io.ddd4j.sample.javalin.shiro.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.shiro.goods.web.GoodsQueryResource;
import io.ddd4j.sample.javalin.shiro.goods.web.GoodsResource;
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

/**
 * Javalin + Guice + Apache Shiro + RBAC 鉴权 + DDD 业务示例启动类。
 *
 * <h3>核心要点</h3>
 * <p>Javalin 本身<b>没有 DI 容器</b>，使用 Google Guice 注入业务服务。
 * 同时，ddd4j 的 4 个核心 SPI（DomainEventPublisher / MQEventPublisher /
 * SubjectProvider / I18nProvider）需要手动注册到 {@link BaseContext} 才能被
 * 业务代码通过 {@link io.ddd4j.core.context.Contexts#inject} 查找到。
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
public class JavalinShiroApplication {

    /**
     * Javalin 监听端口
     */
    public static final int PORT = 7000;

    public static void main(String[] args) {
        // ==================== 1. 初始化 RBAC + Shiro SecurityManager ====================
        // 先构造 RBAC 仓储和种子数据（Shiro 与 Guice 共享同一份数据）
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryRoleRepository roleRepository = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissionRepository = new InMemoryPermissionRepository();
        RbacService rbacService = new RbacService(userRepository, roleRepository, permissionRepository);
        RbacConfig.initSeedData(rbacService);

        // 初始化 Shiro SecurityManager（基于 RBAC Realm）
        AuthConfig.initShiro(userRepository, roleRepository, rbacService);

        // ==================== 2. 创建 Guice 注入器（共享 RBAC 仓储）====================
        Module authModule = new AuthModule(userRepository, roleRepository, permissionRepository, rbacService);
        Injector injector = Guice.createInjector(authModule);

        // ==================== 3. 把 4 个核心 SPI 注入到 BaseContext ====================
        SubjectProvider subjectProvider = new ShiroSubjectProvider();
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider);
        SubjectKit.register(subjectProvider);

        SubjectDataProvider subjectDataProvider = injector.getInstance(SubjectDataProvider.class);
        SubjectKit.setDataProvider(subjectDataProvider);

        DomainEventPublisher domainEventPublisher = new NoOpDomainEventPublisher();
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, domainEventPublisher);

        MQEventPublisher mqEventPublisher = new NoOpMQEventPublisher();
        BaseContext.inject(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class, mqEventPublisher);

        I18nProvider i18nProvider = new DefaultI18nProvider();
        BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider);

        SubjectKit.setStrategy(SubjectStrategy.instance);

        // ==================== 3.7 注册 Goods 仓储到 RepositoryRegistry（充血查询需要） ====================
        RepositoryRegistry.register(Goods.class, new InMemoryGoodsRepository());

        // ==================== 4. 获取 RBAC 控制器与 Resource 实例 ====================
        AuthenticationController authController = injector.getInstance(AuthenticationController.class);
        AuthorizationController authzController = injector.getInstance(AuthorizationController.class);
        OrderResource orderResource = injector.getInstance(OrderResource.class);
        GoodsResource goodsResource = injector.getInstance(GoodsResource.class);
        GoodsQueryResource goodsQueryResource = injector.getInstance(GoodsQueryResource.class);

        // ==================== 5. 启动 Javalin 并注册路由 ====================
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.routes.apiBuilder(() -> {
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
                        ctx.status(403).json(R.fail(403, "forbidden: requires order:pay permission"));
                        return;
                    }
                    String id = ctx.pathParam("id");
                    ctx.json(R.ok("order pay authorized", java.util.Map.of(
                            "orderId", id,
                            "byUser", String.valueOf(SubjectKit.getLoginId()))));
                });

                // Order 路由（通过 EndpointGroup 暴露）
                ApiBuilder.path("orders", () -> orderResource.routes());

                // Goods 路由（写侧 + 读侧合并到 /api/goods 命名空间）
                ApiBuilder.path("api/goodss", () -> {
                    goodsResource.routes();
                    goodsQueryResource.routes();
                });
            });
        });

        app.start(PORT);
        System.out.println("Javalin + Shiro + RBAC 鉴权 + DDD 业务示例启动于 http://localhost:" + PORT);
        System.out.println("  Authentication：/auth/login, /auth/me, /auth/check/permission ...");
        System.out.println("  Authorization (RBAC)：/auth/users, /auth/roles, /auth/permissions ...");
        System.out.println("  Order 业务：/orders, /orders/{id}, /orders/{id}/pay ...");
        System.out.println("  Goods 业务：/api/goodss, /api/goodss/page, /api/goodss/list ...");
        System.out.println("  业务鉴权示范：POST /auth/orders/{id}/pay 需要 order:pay 权限");
    }

    // ==================== 示例 SPI 实现（仅本示例使用） ====================

    private static class NoOpDomainEventPublisher implements DomainEventPublisher {
        @Override
        public <T> void publish(io.ddd4j.core.ddd.event.DomainEvent<T> event) {
            System.out.println("[DomainEvent] " + event.getClass().getSimpleName());
        }

        @Override
        public <T> void publishAll(java.util.Collection<io.ddd4j.core.ddd.event.DomainEvent<T>> events) {
            if (events != null) {
                events.forEach(this::publish);
            }
        }
    }

    private static class NoOpMQEventPublisher implements MQEventPublisher {
        @Override
        public void publish(io.ddd4j.core.event.MQEvent event) {
            System.out.println("[MQEvent] " + event.getClass().getSimpleName());
        }
    }

    private static class DefaultI18nProvider implements I18nProvider {
        @Override
        public String getMessage(String key, Object... args) {
            return I18nProvider.DEFAULT.getMessage(key, args);
        }
    }

}