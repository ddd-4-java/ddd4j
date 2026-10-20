package io.ddd4j.sample.javalin.shiro.rbac;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacConfigContractTest {

    @Test
    void returnsImmutablePermissionAndRoleSnapshots() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryRoleRepository roles = new InMemoryRoleRepository();
        RbacConfig.initSeedData(new RbacService(users, roles, new InMemoryPermissionRepository()));
        SubjectDataProvider provider = RbacConfig.createSubjectDataProvider(users, roles);
        AuthPrincipal principal = new AuthPrincipal().setLoginId("zhangsan");

        List<String> permissions = provider.getPermissionList(principal);
        List<String> roleCodes = provider.getRoleList(principal);
        assertTrue(permissions.contains("order:pay"));
        assertTrue(roleCodes.contains("user"));
        assertThrows(UnsupportedOperationException.class, () -> permissions.add("admin:*"));
        assertThrows(UnsupportedOperationException.class, () -> roleCodes.add("admin"));
    }
}
