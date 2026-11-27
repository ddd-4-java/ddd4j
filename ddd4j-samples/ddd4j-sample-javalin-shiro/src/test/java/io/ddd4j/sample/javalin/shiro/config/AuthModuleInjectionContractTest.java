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

/** \u901a\u8fc7 <code>Guice.createInjector()</code>\u3001<code>injector.getInstance()</code>\u3001<code>users.count()</code> \u9a8c\u8bc1 <code>AuthModuleInjectionContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class AuthModuleInjectionContractTest {

    /** \u9a8c\u8bc1<code>users.count()</code> \u7b49\u4e8e <code>3</code>\uff0c<code>roles.count()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>subjectData.getPermissionList(principal)</code> \u7b49\u4e8e <code>Collections.singletonList(permissionCode)</code>\u3002 */
    @Test
    void bindsSeededRepositoriesServiceAndSubjectDataProvider() {
        Injector injector = Guice.createInjector(new AuthModule());
        InMemoryUserRepository users = injector.getInstance(InMemoryUserRepository.class);
        InMemoryRoleRepository roles = injector.getInstance(InMemoryRoleRepository.class);
        InMemoryPermissionRepository permissions = injector.getInstance(InMemoryPermissionRepository.class);
        RbacService rbac = injector.getInstance(RbacService.class);
        SubjectDataProvider subjectData = injector.getInstance(SubjectDataProvider.class);
        AuthPrincipal principal = new AuthPrincipal().setLoginId("lisi");

        assertEquals(3, users.count());
        assertEquals(2, roles.count());
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

    /** \u9a8c\u8bc1<code>second.getInstance(InMemoryUserRepository.class)</code> \u4e0e\u2026\u4e3a\u4e0d\u540c\u7684\u5b9e\u4f8b <code>first.getInstance(InMemoryUserRepository.class)</code>\uff0c<code>second.getInstance(InMemoryRoleRepository.class)</code> \u4e0e\u2026\u4e3a\u4e0d\u540c\u7684\u5b9e\u4f8b <code>first.getInstance(InMemoryRoleRepository.class)</code>\uff0c<code>reinstalled.getInstance(InMemoryRoleRepository.class).findByCode("user").isPresent()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>injector.getInstance(GoodsApplicationService.class)</code>\u975e\u7a7a\uff0c<code>injector.getInstance(InMemoryUserRepository.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>users</code>\uff0c<code>roles.count()</code> \u7b49\u4e8e <code>0</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void rejectsMissingExternallyOwnedRbacService() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryRoleRepository roles = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissions = new InMemoryPermissionRepository();

        assertThrows(NullPointerException.class, () -> new AuthModule(users, roles, permissions, null));
    }
}
