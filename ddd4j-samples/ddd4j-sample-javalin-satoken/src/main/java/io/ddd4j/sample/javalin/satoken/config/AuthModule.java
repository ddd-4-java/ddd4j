package io.ddd4j.sample.javalin.satoken.config;

import com.google.inject.AbstractModule;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.javalin.satoken.goods.application.GoodsApplicationService;
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

/**
 * RBAC + 业务 Guice 模块：注册权限数据源与所有业务服务/资源绑定。
 *
 * <p>Javalin 没有 DI 容器，使用 Google Guice 代替 Spring @Configuration / CDI @ApplicationScoped。
 * RBAC 业务代码（User/Role/Permission/Repository/Service）与 Spring 示例完全一致，
 * 仅路由层（Controller）采用 Javalin {@link io.javalin.apibuilder.ApiBuilder} 风格。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AuthModule extends AbstractModule {

    @Override
    protected void configure() {
        // ========================= RBAC 模块 =========================
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryRoleRepository roleRepository = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissionRepository = new InMemoryPermissionRepository();

        // 暴露仓储接口到具体实现
        bind(UserRepository.class).toInstance(userRepository);
        bind(RoleRepository.class).toInstance(roleRepository);
        bind(PermissionRepository.class).toInstance(permissionRepository);

        RbacService rbacService = new RbacService(userRepository, roleRepository, permissionRepository);
        bind(RbacService.class).toInstance(rbacService);

        // 注册 SubjectDataProvider：RbacService 本身实现了 SubjectDataProvider
        SubjectDataProvider subjectDataProvider = RbacConfig.subjectDataProvider(rbacService);
        bind(SubjectDataProvider.class).toInstance(subjectDataProvider);

        // 绑定 RBAC 控制器
        bind(AuthenticationController.class);
        bind(AuthorizationController.class);

        // ========================= Order 模块 =========================
        bind(OrderRepository.class).to(InMemoryOrderRepository.class);
        bind(OrderDomainService.class);
        bind(OrderApplicationService.class);
        bind(OrderResource.class);

        // ========================= Goods 模块 =========================
        bind(GoodsRepository.class).to(InMemoryGoodsRepository.class);
        bind(GoodsApplicationService.class);
        bind(GoodsResource.class);
        bind(GoodsQueryResource.class);

        // RepositoryRegistry 的 register 是静态方法，在 Guice 启动后由 JavalinSaTokenApplication 调用
    }

}