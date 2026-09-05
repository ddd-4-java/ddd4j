package io.ddd4j.sample.javalin.shiro.rbac.controller;

import java.util.Objects;

import com.google.inject.Inject;
import io.ddd4j.core.api.R;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.javalin.shiro.rbac.domain.Permission;
import io.ddd4j.sample.javalin.shiro.rbac.domain.Role;
import io.ddd4j.sample.javalin.shiro.rbac.domain.User;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import io.javalin.http.Context;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 授权管理控制器：用户 / 角色 / 权限的 CRUD。
 *
 * <p>本控制器演示 RBAC 体系下的授权元数据管理能力。
 * 所有写操作均通过 {@link SubjectKit#hasRole} / {@link SubjectKit#hasPermission} 做权限校验：
 * <ul>
 *   <li>创建/更新/删除用户 → 需要 {@code admin} 角色</li>
 *   <li>创建/更新/删除角色 → 需要 {@code admin} 角色</li>
 *   <li>创建/删除权限 → 需要 {@code admin} 角色</li>
 * </ul>
 *
 * <p>路由注册在 {@link io.ddd4j.sample.javalin.shiro.JavalinShiroApplication} 中完成。
 *
 * <p>本控制器与 {@code ddd4j-sample-javalin-satoken} 的 AuthorizationController 业务代码<b>完全一致</b>，
 * 仅 HTTP 适配层（Javalin lambda）适配不同框架。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AuthorizationController {

    private final RbacService rbacService;

    @Inject
    public AuthorizationController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    // ============================ User CRUD ============================

    /**
     * 数组 → Set 转换（保留顺序）。
     */
    private static Set<String> toSet(String[] arr) {
        if (Objects.isNull(arr)) {
            return new LinkedHashSet<>();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String s : arr) {
            set.add(s);
        }
        return set;
    }

    private static UserView toUserView(User user) {
        return new UserView(user.loginId(), user.displayName(),
                new LinkedHashSet<>(user.roles()),
                new LinkedHashSet<>(user.permissions()));
    }

    private static RoleView toRoleView(Role role) {
        return new RoleView(role.code(), role.name(), new LinkedHashSet<>(role.permissions()));
    }

    private static PermissionView toPermissionView(Permission permission) {
        return new PermissionView(permission.code(), permission.description());
    }

    /**
     * GET /auth/users —— 查询用户列表（需要 user:list 权限）。
     */
    public void listUsers(Context ctx) {
        if (!SubjectKit.hasPermission("user:list")) {
            ctx.status(403).json(R.fail(403, "forbidden: requires user:list"));
            return;
        }
        Collection<User> users = rbacService.listUsers();
        ctx.json(R.ok(users.stream().map(AuthorizationController::toUserView).toList()));
    }

    // ============================ Role CRUD ============================

    /**
     * GET /auth/users/{id} —— 查询单个用户。
     */
    public void getUser(Context ctx) {
        if (!SubjectKit.hasPermission("user:list")) {
            ctx.status(403).json(R.fail(403, "forbidden: requires user:list"));
            return;
        }
        String loginId = ctx.pathParam("id");
        User user = rbacService.findUser(loginId);
        ctx.json(R.ok(toUserView(user)));
    }

    /**
     * POST /auth/users —— 创建用户（需要 admin 角色）。
     */
    public void createUser(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        CreateUserRequest req = ctx.bodyAsClass(CreateUserRequest.class);
        User user = rbacService.createUser(
                req.loginId(),
                req.password(),
                req.displayName(),
                toSet(req.roles()),
                toSet(req.permissions()));
        ctx.status(201).json(R.ok("user created", toUserView(user)));
    }

    /**
     * PUT /auth/users/{id} —— 更新用户（需要 admin 角色）。
     */
    public void updateUser(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        String loginId = ctx.pathParam("id");
        UpdateUserRequest req = ctx.bodyAsClass(UpdateUserRequest.class);
        User user = rbacService.updateUser(
                loginId,
                req.displayName(),
                req.password(),
                Objects.nonNull(req.roles()) ? toSet(req.roles()) : null,
                Objects.nonNull(req.permissions()) ? toSet(req.permissions()) : null);
        ctx.json(R.ok("user updated", toUserView(user)));
    }

    /**
     * DELETE /auth/users/{id} —— 删除用户（需要 admin 角色 + user:delete 权限组合）。
     */
    public void deleteUser(Context ctx) {
        // 组合校验：必须同时拥有 admin 角色 + user:delete 权限
        if (!SubjectKit.hasRole("admin") || !SubjectKit.hasPermission("user:delete")) {
            ctx.status(403).json(R.fail(403, "forbidden: requires admin role and user:delete permission"));
            return;
        }
        String loginId = ctx.pathParam("id");
        rbacService.deleteUser(loginId);
        ctx.json(R.ok("user deleted", Map.of("loginId", loginId)));
    }

    // ============================ Permission CRUD ============================

    /**
     * GET /auth/roles —— 查询角色列表（需要 role:list 权限）。
     */
    public void listRoles(Context ctx) {
        if (!SubjectKit.hasPermission("role:list")) {
            ctx.status(403).json(R.fail(403, "forbidden: requires role:list"));
            return;
        }
        Collection<Role> roles = rbacService.listRoles();
        ctx.json(R.ok(roles.stream().map(AuthorizationController::toRoleView).toList()));
    }

    /**
     * POST /auth/roles —— 创建角色（需要 admin 角色）。
     */
    public void createRole(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        CreateRoleRequest req = ctx.bodyAsClass(CreateRoleRequest.class);
        Role role = rbacService.createRole(req.code(), req.name(), toSet(req.permissions()));
        ctx.status(201).json(R.ok("role created", toRoleView(role)));
    }

    /**
     * PUT /auth/roles/{code} —— 更新角色（需要 admin 角色）。
     */
    public void updateRole(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        String code = ctx.pathParam("code");
        UpdateRoleRequest req = ctx.bodyAsClass(UpdateRoleRequest.class);
        Role role = rbacService.updateRole(code, req.name(),
                Objects.nonNull(req.permissions()) ? toSet(req.permissions()) : null);
        ctx.json(R.ok("role updated", toRoleView(role)));
    }

    // ============================ 私有工具方法 ============================

    /**
     * DELETE /auth/roles/{code} —— 删除角色（需要 admin 角色）。
     */
    public void deleteRole(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        String code = ctx.pathParam("code");
        rbacService.deleteRole(code);
        ctx.json(R.ok("role deleted", Map.of("code", code)));
    }

    /**
     * GET /auth/permissions —— 查询权限列表（需要 permission:list 权限）。
     */
    public void listPermissions(Context ctx) {
        if (!SubjectKit.hasPermission("permission:list")) {
            ctx.status(403).json(R.fail(403, "forbidden: requires permission:list"));
            return;
        }
        Collection<Permission> perms = rbacService.listPermissions();
        ctx.json(R.ok(perms.stream().map(AuthorizationController::toPermissionView).toList()));
    }

    /**
     * POST /auth/permissions —— 创建权限（需要 admin 角色）。
     */
    public void createPermission(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        CreatePermissionRequest req = ctx.bodyAsClass(CreatePermissionRequest.class);
        Permission perm = rbacService.createPermission(req.code(), req.description());
        ctx.status(201).json(R.ok("permission created", toPermissionView(perm)));
    }

    /**
     * DELETE /auth/permissions/{code} —— 删除权限（需要 admin 角色）。
     */
    public void deletePermission(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        String code = ctx.pathParam("code");
        rbacService.deletePermission(code);
        ctx.json(R.ok("permission deleted", Map.of("code", code)));
    }

    /**
     * 校验当前会话是否为 admin 角色，否则直接返回 403 响应。
     */
    private boolean requireAdmin(Context ctx) {
        if (!SubjectKit.hasRole("admin")) {
            ctx.status(403).json(R.fail(403, "forbidden: requires admin role"));
            return false;
        }
        return true;
    }

    // ============================ DTO 视图对象 ============================

    public record CreateUserRequest(String loginId,
                                    String password,
                                    String displayName,
                                    String[] roles,
                                    String[] permissions) {
    }

    public record UpdateUserRequest(String displayName,
                                    String password,
                                    String[] roles,
                                    String[] permissions) {
    }

    public record CreateRoleRequest(String code, String name, String[] permissions) {
    }

    public record UpdateRoleRequest(String name, String[] permissions) {
    }

    public record CreatePermissionRequest(String code, String description) {
    }

    public record UserView(String loginId,
                           String displayName,
                           Set<String> roles,
                           Set<String> permissions) {
    }

    public record RoleView(String code, String name, Set<String> permissions) {
    }

    public record PermissionView(String code, String description) {
    }

}