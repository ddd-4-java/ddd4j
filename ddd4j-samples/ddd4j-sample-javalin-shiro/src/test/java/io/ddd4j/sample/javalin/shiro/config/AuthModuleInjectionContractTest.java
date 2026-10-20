package io.ddd4j.sample.javalin.shiro.config;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.sample.javalin.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.shiro.order.application.OrderApplicationService;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthModuleInjectionContractTest {

    @Test
    void bindsOrderAndGoodsApplicationServices() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryRoleRepository roles = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissions = new InMemoryPermissionRepository();
        RbacService rbac = new RbacService(users, roles, permissions);
        Injector injector = Guice.createInjector(new AuthModule(users, roles, permissions, rbac));
        assertNotNull(injector.getInstance(OrderApplicationService.class));
        assertNotNull(injector.getInstance(GoodsApplicationService.class));
    }
}
