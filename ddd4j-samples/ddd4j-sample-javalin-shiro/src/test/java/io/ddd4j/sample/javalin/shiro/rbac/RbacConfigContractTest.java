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

/** \u901a\u8fc7 <code>RbacConfig.initSeedData()</code>\u3001<code>RbacConfig.createSubjectDataProvider()</code>\u3001<code>provider.getPermissionList()</code> \u9a8c\u8bc1 <code>RbacConfigContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class RbacConfigContractTest {

    /** \u9a8c\u8bc1<code>roleCodes</code> \u7b49\u4e8e <code>Arrays.asList("user")</code>\uff0c<code>permissions.add("admin:*")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>roleCodes.add("admin")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>provider.getPermissionList(new AuthPrincipal().setLoginId("ordered"))</code> \u7b49\u4e8e <code>Arrays.asList("z", "a")</code>\u3002 */
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
