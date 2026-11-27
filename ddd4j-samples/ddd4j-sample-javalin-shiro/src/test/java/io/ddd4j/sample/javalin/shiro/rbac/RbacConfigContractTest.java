package io.ddd4j.sample.javalin.shiro.rbac;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.javalin.shiro.rbac.domain.User;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 通过 <code>RbacConfig.initSeedData()</code>、<code>RbacConfig.createSubjectDataProvider()</code>、<code>provider.getPermissionList()</code> 验证 <code>RbacConfigContract</code> 的运行行为。
 */
class RbacConfigContractTest {

    /**
     * 验证<code>roleCodes</code> 等于 <code>Arrays.asList("user")</code>，<code>permissions.add("admin:*")</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>roleCodes.add("admin")</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void returnsImmutablePermissionAndRoleSnapshots() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryRoleRepository roles = new InMemoryRoleRepository();
        RbacConfig.initSeedData(new RbacService(users, roles, new InMemoryPermissionRepository()));
        SubjectDataProvider provider = RbacConfig.createSubjectDataProvider(users, roles);
        AuthPrincipal principal = new AuthPrincipal().setLoginId("zhangsan");

        List<String> permissions = provider.getPermissionList(principal);
        List<String> roleCodes = provider.getRoleList(principal);
        assertEquals(Arrays.asList("order:pay", "user:list", "order:create"), permissions);
        assertEquals(Arrays.asList("user"), roleCodes);
        assertThrows(UnsupportedOperationException.class, () -> permissions.add("admin:*"));
        assertThrows(UnsupportedOperationException.class, () -> roleCodes.add("admin"));
    }

    /**
     * 验证<code>provider.getPermissionList(new AuthPrincipal().setLoginId("ordered"))</code> 等于 <code>Arrays.asList("z", "a")</code>。
     */
    @Test
    void preservesDirectPermissionInsertionOrder() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryRoleRepository roles = new InMemoryRoleRepository();
        users.save(new User("ordered", "password", "Ordered", Collections.emptySet(),
                new LinkedHashSet<>(Arrays.asList("z", "a"))));
        SubjectDataProvider provider = RbacConfig.createSubjectDataProvider(users, roles);

        assertEquals(Arrays.asList("z", "a"),
                provider.getPermissionList(new AuthPrincipal().setLoginId("ordered")));
    }
}
