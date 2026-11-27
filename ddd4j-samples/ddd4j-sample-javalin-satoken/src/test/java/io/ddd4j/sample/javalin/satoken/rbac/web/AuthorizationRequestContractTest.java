package io.ddd4j.sample.javalin.satoken.rbac.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** \u901a\u8fc7 <code>mapper.readValue()</code>\u3001<code>request.userId()</code>\u3001<code>request.username()</code> \u9a8c\u8bc1 <code>AuthorizationRequestContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class AuthorizationRequestContractTest {

    /**
     * \u8c03\u7528 <code>constructed.hashCode()</code>\u3001<code>bean.hashCode()</code>\u3001<code>Modifier.isFinal()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param bean \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>Object</code> \u8f93\u5165
     * @param constructed \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>Object</code> \u8f93\u5165
     */
    private static void valueContract(Object bean, Object constructed) {
        assertEquals(constructed, bean);
        assertEquals(constructed.hashCode(), bean.hashCode());
        assertFalse(Modifier.isFinal(bean.getClass().getModifiers()));
    }

    /**
     * \u9a8c\u8bc1<code>request.userId()</code> \u7b49\u4e8e <code>"u1"</code>\uff0c<code>request.username()</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>mapper.readValue(mapper.writeValueAsString(request), AuthorizationController.CreateUserRequest.class)</code> \u7b49\u4e8e <code>request</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void createUserRequestRetainsJsonFieldContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AuthorizationController.CreateUserRequest request = mapper.readValue(
                "{\"userId\":\"u1\",\"username\":\"alice\",\"password\":\"secret\",\"realName\":\"Alice\"}",
                AuthorizationController.CreateUserRequest.class);
        assertEquals("u1", request.userId());
        assertEquals("alice", request.username());
        assertEquals("secret", request.password());
        assertEquals("Alice", request.realName());
        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request),
                AuthorizationController.CreateUserRequest.class));
    }

    /** \u9a8c\u8bc1<code>create.userId()</code> \u7b49\u4e8e <code>create.getUserId()</code>\uff0c<code>create.username()</code> \u7b49\u4e8e <code>create.getUsername()</code>\uff0c<code>update.toString().contains("new-secret")</code>\u4e3a\u5047\u3002 */
    @Test
    void userRequestsRetainBeanAndComponentContracts() {
        AuthorizationController.CreateUserRequest create = new AuthorizationController.CreateUserRequest();
        create.setUserId("u1");
        create.setUsername("alice");
        create.setPassword("secret");
        create.setRealName("Alice");
        assertEquals(create.getUserId(), create.userId());
        assertEquals(create.getUsername(), create.username());
        assertEquals(create.getPassword(), create.password());
        assertEquals(create.getRealName(), create.realName());
        valueContract(create, new AuthorizationController.CreateUserRequest("u1", "alice", "secret", "Alice"));
        assertFalse(create.toString().contains("secret"));

        User.Status status = User.Status.values()[0];
        AuthorizationController.UpdateUserRequest update = new AuthorizationController.UpdateUserRequest();
        update.setRealName("Alice");
        update.setPassword("new-secret");
        update.setStatus(status);
        assertEquals(update.getRealName(), update.realName());
        assertEquals(update.getPassword(), update.password());
        assertEquals(update.getStatus(), update.status());
        valueContract(update, new AuthorizationController.UpdateUserRequest("Alice", "new-secret", status));
        assertFalse(update.toString().contains("new-secret"));
    }

    /** \u9a8c\u8bc1<code>create.roleId()</code> \u7b49\u4e8e <code>create.getRoleId()</code>\uff0c<code>create.roleCode()</code> \u7b49\u4e8e <code>create.getRoleCode()</code>\uff0c<code>update.status()</code> \u7b49\u4e8e <code>update.getStatus()</code>\u3002 */
    @Test
    void roleRequestsRetainBeanAndComponentContracts() {
        AuthorizationController.CreateRoleRequest create = new AuthorizationController.CreateRoleRequest();
        create.setRoleId("r1");
        create.setRoleCode("admin");
        create.setRoleName("Admin");
        create.setDescription("manage");
        assertEquals(create.getRoleId(), create.roleId());
        assertEquals(create.getRoleCode(), create.roleCode());
        assertEquals(create.getRoleName(), create.roleName());
        assertEquals(create.getDescription(), create.description());
        valueContract(create, new AuthorizationController.CreateRoleRequest("r1", "admin", "Admin", "manage"));

        Role.Status status = Role.Status.values()[0];
        AuthorizationController.UpdateRoleRequest update = new AuthorizationController.UpdateRoleRequest();
        update.setRoleName("Admin");
        update.setDescription("manage");
        update.setStatus(status);
        assertEquals(update.getRoleName(), update.roleName());
        assertEquals(update.getDescription(), update.description());
        assertEquals(update.getStatus(), update.status());
        valueContract(update, new AuthorizationController.UpdateRoleRequest("Admin", "manage", status));
    }

    /** \u9a8c\u8bc1<code>create.permissionId()</code> \u7b49\u4e8e <code>create.getPermissionId()</code>\uff0c<code>create.permissionCode()</code> \u7b49\u4e8e <code>create.getPermissionCode()</code>\uff0c<code>update.status()</code> \u7b49\u4e8e <code>update.getStatus()</code>\u3002 */
    @Test
    void permissionRequestsRetainBeanAndComponentContracts() {
        AuthorizationController.CreatePermissionRequest create = new AuthorizationController.CreatePermissionRequest();
        create.setPermissionId("p1");
        create.setPermissionCode("goods:read");
        create.setPermissionName("Read goods");
        create.setModule("goods");
        assertEquals(create.getPermissionId(), create.permissionId());
        assertEquals(create.getPermissionCode(), create.permissionCode());
        assertEquals(create.getPermissionName(), create.permissionName());
        assertEquals(create.getModule(), create.module());
        valueContract(create, new AuthorizationController.CreatePermissionRequest(
                "p1", "goods:read", "Read goods", "goods"));

        Permission.Status status = Permission.Status.values()[0];
        AuthorizationController.UpdatePermissionRequest update =
                new AuthorizationController.UpdatePermissionRequest();
        update.setPermissionName("Read goods");
        update.setModule("goods");
        update.setStatus(status);
        assertEquals(update.getPermissionName(), update.permissionName());
        assertEquals(update.getModule(), update.module());
        assertEquals(update.getStatus(), update.status());
        valueContract(update, new AuthorizationController.UpdatePermissionRequest("Read goods", "goods", status));
    }

    /** \u9a8c\u8bc1<code>roles.roleIds()</code> \u7b49\u4e8e <code>roles.getRoleIds()</code>\uff0c<code>permissions.permissionIds()</code> \u7b49\u4e8e <code>permissions.getPermissionIds()</code>\u3002 */
    @Test
    void assignmentRequestsRetainBeanAndComponentContracts() {
        AuthorizationController.AssignRolesRequest roles = new AuthorizationController.AssignRolesRequest();
        roles.setRoleIds(new ArrayList<>(Arrays.asList("admin", "auditor")));
        assertEquals(roles.getRoleIds(), roles.roleIds());
        valueContract(roles, new AuthorizationController.AssignRolesRequest(
                new ArrayList<>(Arrays.asList("admin", "auditor"))));

        AuthorizationController.AssignPermissionsRequest permissions =
                new AuthorizationController.AssignPermissionsRequest();
        permissions.setPermissionIds(new ArrayList<>(Arrays.asList("goods:read", "goods:write")));
        assertEquals(permissions.getPermissionIds(), permissions.permissionIds());
        valueContract(permissions, new AuthorizationController.AssignPermissionsRequest(
                new ArrayList<>(Arrays.asList("goods:read", "goods:write"))));
    }
}
