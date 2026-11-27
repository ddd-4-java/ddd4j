package io.ddd4j.sample.javalin.shiro.config;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.javalin.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>Guice.createInjector()</code>、<code>injector.getInstance()</code>、<code>users.count()</code> 验证 <code>AuthModuleInjectionContract</code> 的运行行为。
 */
class AuthModuleInjectionContractTest {

    /**
     * 验证仓储注入、现有账号的角色和权限，以及商品交易只读账号的权限边界。
     */
    @Test
    void bindsSeededRepositoriesServiceAndSubjectDataProvider() {
        Injector injector = Guice.createInjector(new AuthModule());
        InMemoryUserRepository users = injector.getInstance(InMemoryUserRepository.class);
        InMemoryRoleRepository roles = injector.getInstance(InMemoryRoleRepository.class);
        InMemoryPermissionRepository permissions = injector.getInstance(InMemoryPermissionRepository.class);
        RbacService rbac = injector.getInstance(RbacService.class);
        SubjectDataProvider subjectData = injector.getInstance(SubjectDataProvider.class);
        AuthPrincipal principal = new AuthPrincipal().setLoginId("lisi");

        assertEquals(4, users.count());
        assertEquals(3, roles.count());
        assertTrue(users.findByLoginId("reader").isPresent());
        AuthPrincipal reader = new AuthPrincipal().setLoginId("reader");
        assertEquals(Collections.singletonList("reader"), subjectData.getRoleList(reader));
        assertEquals(Collections.singletonList("order:read"), subjectData.getPermissionList(reader));
        assertSame(rbac.findUser("lisi"), users.findByLoginId("lisi").orElseThrow(AssertionError::new));
        assertSame(rbac.findRole("user"), roles.findByCode("user").orElseThrow(AssertionError::new));
        assertEquals(Collections.singletonList("user"), subjectData.getRoleList(principal));
        assertEquals(rbac.findRole("user").permissions(),
                new HashSet<>(subjectData.getPermissionList(principal)));

        String permissionCode = "order:review";
        assertSame(rbac.createPermission(permissionCode, "Review an order"),
                permissions.findByCode(permissionCode).orElseThrow(AssertionError::new));
        rbac.updateRole("user", "Reviewer", Collections.singleton(permissionCode));
        assertEquals(Collections.singletonList(permissionCode), subjectData.getPermissionList(principal));
    }

    /**
     * 验证<code>second.getInstance(InMemoryUserRepository.class)</code> 与…为不同的实例 <code>first.getInstance(InMemoryUserRepository.class)</code>，<code>second.getInstance(InMemoryRoleRepository.class)</code> 与…为不同的实例 <code>first.getInstance(InMemoryRoleRepository.class)</code>，<code>reinstalled.getInstance(InMemoryRoleRepository.class).findByCode("user").isPresent()</code>为假。
     */
    @Test
    void isolatesStandaloneModulesAndDoesNotReseedOnReinstallation() {
        AuthModule firstModule = new AuthModule();
        Injector first = Guice.createInjector(firstModule);
        RbacService firstRbac = first.getInstance(RbacService.class);
        firstRbac.deleteRole("user");

        Injector second = Guice.createInjector(new AuthModule());
        assertNotSame(first.getInstance(InMemoryUserRepository.class),
                second.getInstance(InMemoryUserRepository.class));
        assertNotSame(first.getInstance(InMemoryRoleRepository.class),
                second.getInstance(InMemoryRoleRepository.class));
        assertNotSame(first.getInstance(InMemoryPermissionRepository.class),
                second.getInstance(InMemoryPermissionRepository.class));
        assertNotSame(firstRbac, second.getInstance(RbacService.class));
        assertEquals("user", second.getInstance(RbacService.class).findRole("user").code());

        Injector reinstalled = Guice.createInjector(firstModule);
        assertSame(firstRbac, reinstalled.getInstance(RbacService.class));
        assertFalse(reinstalled.getInstance(InMemoryRoleRepository.class).findByCode("user").isPresent());
    }

    /**
     * 验证<code>injector.getInstance(GoodsApplicationService.class)</code>非空，<code>injector.getInstance(InMemoryUserRepository.class)</code> 与…为同一实例 <code>users</code>，<code>roles.count()</code> 等于 <code>0</code>。
     */
    @Test
    void bindsGoodsAndRbacApplicationServices() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryRoleRepository roles = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissions = new InMemoryPermissionRepository();
        RbacService rbac = new RbacService(users, roles, permissions);
        Injector injector = Guice.createInjector(new AuthModule(users, roles, permissions, rbac));
        assertNotNull(injector.getInstance(GoodsApplicationService.class));
        assertSame(users, injector.getInstance(InMemoryUserRepository.class));
        assertSame(roles, injector.getInstance(InMemoryRoleRepository.class));
        assertSame(permissions, injector.getInstance(InMemoryPermissionRepository.class));
        assertSame(rbac, injector.getInstance(RbacService.class));
        assertEquals(0, users.count());
        assertEquals(0, roles.count());
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void rejectsMissingExternallyOwnedRbacService() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryRoleRepository roles = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissions = new InMemoryPermissionRepository();

        assertThrows(NullPointerException.class, () -> new AuthModule(users, roles, permissions, null));
    }
}
