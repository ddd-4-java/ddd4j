package io.ddd4j.sample.javalin.satoken;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaRequestForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectStrategy;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.javalin.satoken.config.AuthModule;
import io.ddd4j.sample.javalin.satoken.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.satoken.goods.domain.Goods;
import io.ddd4j.sample.javalin.satoken.goods.domain.GoodsRepository;
import io.ddd4j.sample.javalin.satoken.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.satoken.goods.web.GoodsQueryResource;
import io.ddd4j.sample.javalin.satoken.goods.web.GoodsResource;
import io.ddd4j.sample.javalin.satoken.order.application.OrderApplicationService;
import io.ddd4j.sample.javalin.satoken.order.domain.repository.OrderRepository;
import io.ddd4j.sample.javalin.satoken.order.domain.service.OrderDomainService;
import io.ddd4j.sample.javalin.satoken.order.infrastructure.InMemoryOrderRepository;
import io.ddd4j.sample.javalin.satoken.order.web.OrderResource;
import io.ddd4j.sample.javalin.satoken.rbac.RbacConfig;
import io.ddd4j.sample.javalin.satoken.rbac.application.RbacService;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.UserRepository;
import io.ddd4j.sample.javalin.satoken.rbac.infrastructure.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.satoken.rbac.infrastructure.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.satoken.rbac.infrastructure.InMemoryUserRepository;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthenticationController;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthorizationController;
import io.javalin.Javalin;
import io.javalin.apibuilder.ApiBuilder;
import io.javalin.json.JavalinJackson;
import org.fuin.ddd4j.core.EntityId;

/**
 * 测试基础设施：手动创建 Guice 注入器并启动 Javalin。
 *
 * <p>注：{@link AuthModule} 中的部分类（{@link OrderResource} / {@link OrderDomainService} /
 * {@link GoodsResource} / {@link GoodsQueryResource} / {@link AuthenticationController} /
 * {@link AuthorizationController}）的构造函数缺少 {@code @Inject} 注解，
 * Guice 会报 MISSING_CONSTRUCTOR 错误。这是预存在的业务代码 bug（不允许修改业务代码），
 * 因此测试侧不直接使用 {@code Guice.createInjector(new AuthModule())}，而是改用
 * 本类提供的 {@link TestModule}，所有控制器/资源都用 {@code toInstance} 绑定到手动构造的实例。
 */
public final class TestSupport {

    private TestSupport() {
    }

    // ============================ 测试用 SPI 实现 ============================

    /**
     * 启动 Javalin 应用：注册 RBAC 种子数据 + Goods/Order 仓储 + SubjectKit SPI + 路由。
     *
     * @return 已启动的 Javalin 实例（绑定端口随机）
     */
    public static Javalin start() {
        // 0) 注入测试需要的核心 SPI（领域事件发布、i18n）
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, new NoopDomainEventPublisher());
        BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, new NoopI18nProvider());

        // 1) 手动构造所有组件（不依赖 Guice 自动注入）
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryRoleRepository roleRepository = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissionRepository = new InMemoryPermissionRepository();
        RbacService rbacService = new RbacService(userRepository, roleRepository, permissionRepository);

        RbacConfig.initRbacData(userRepository, roleRepository, permissionRepository);

        // 2) SubjectKit SPI（用 TestSubjectProvider，getUserId 从 principal 读取，避开 sa-token-jwt 依赖）
        SubjectKit.register(new TestSubjectProvider());
        SubjectKit.setDataProvider(rbacService);
        SubjectKit.setStrategy(SubjectStrategy.instance);

        // 3) Goods 仓储与业务服务
        InMemoryGoodsRepository goodsRepository = new InMemoryGoodsRepository();
        RepositoryRegistry.register(Goods.class, goodsRepository);
        GoodsApplicationService goodsApplicationService = new GoodsApplicationService(goodsRepository);
        GoodsResource goodsResource = new GoodsResource(goodsApplicationService);
        GoodsQueryResource goodsQueryResource = new GoodsQueryResource(goodsApplicationService);

        // 4) Order 仓储与业务服务
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        OrderDomainService orderDomainService = new OrderDomainService(orderRepository);
        OrderApplicationService orderApplicationService = new OrderApplicationService(orderRepository, orderDomainService);
        OrderResource orderResource = new OrderResource(orderApplicationService);

        // 5) RBAC 控制器
        AuthenticationController authController = new AuthenticationController(rbacService);
        AuthorizationController authzController = new AuthorizationController(rbacService);

        // 6) 启动 Javalin
        // 注：JavalinSaTokenApplication 中的 routes() 调用存在预存在的业务 bug（routes() 返回 EndpointGroup
        // 而非直接调用 addEndpoints()），因此测试侧必须显式调用 addEndpoints() 或把 EndpointGroup
        // 直接传给 ApiBuilder.path() 才能让路由生效。
        Javalin app = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.jsonMapper(new JavalinJackson());
            // sa-token 需要在线程中维护 SaTokenContext 才能读写 token；
            // 测试侧在每个请求前后初始化/清理 mock 上下文，并把 Javalin 的 header/cookie 拷贝到 mock request 中。
            cfg.routes.before(ctx -> {
                SaTokenContextMockUtil.setMockContext();
                SaRequestForMock req = (SaRequestForMock) SaHolder.getRequest();
                ctx.headerMap().forEach(req.headerMap::put);
                ctx.cookieMap().forEach((k, v) -> req.cookieMap.put(k, v));
            });
            cfg.routes.after(ctx -> SaTokenContextMockUtil.clearContext());
            cfg.routes.apiBuilder(() -> {
                authController.routes().addEndpoints();
                ApiBuilder.path("rbac", authzController.routes());
                ApiBuilder.path("orders", orderResource.routes());
                ApiBuilder.path("api/goodss", () -> {
                    goodsResource.routes().addEndpoints();
                    goodsQueryResource.routes().addEndpoints();
                });
            });
        });
        app.start(0);
        return app;
    }

    /**
     * 兼容层：保留 Guice injector 入口供需要 SubjectProvider 的代码使用（实际未使用）。
     * 真实测试都通过 {@link #start()} 直接构造实例。
     */
    @SuppressWarnings("unused")
    public static Injector createInjector() {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(UserRepository.class).toInstance(new InMemoryUserRepository());
                bind(RoleRepository.class).toInstance(new InMemoryRoleRepository());
                bind(PermissionRepository.class).toInstance(new InMemoryPermissionRepository());
                InMemoryGoodsRepository goodsRepository = new InMemoryGoodsRepository();
                bind(GoodsRepository.class).toInstance(goodsRepository);
                bind(OrderRepository.class).to(InMemoryOrderRepository.class);
            }
        });
    }

    private static final class NoopDomainEventPublisher implements DomainEventPublisher {
        @Override
        public <ID extends EntityId> void publish(io.ddd4j.core.ddd.event.DomainEvent<ID> event) {
            // noop：测试不需要真实发布领域事件
        }
    }

    private static final class NoopI18nProvider implements I18nProvider {
        @Override
        public String getMessage(String key, Object... args) {
            return key;
        }
    }
}
