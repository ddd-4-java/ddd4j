package io.ddd4j.sample.spring.satoken.rbac.domain.model;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 角色聚合根（RBAC 模型）。
 *
 * <p>角色是权限的容器，用户通过被赋予角色而获得一组权限。
 * 例如：{@code admin} 角色包含 {@code user:add} / {@code user:delete} / {@code user:list} 等权限。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public class Role extends AggregateRoot<String> {

    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID
     */
    private final String roleId;
    /**
     * 角色编码（如 {@code admin}）
     */
    private final String roleCode;
    /**
     * 创建时间
     */
    private final Instant createdAt;
    /**
     * 角色包含的权限 ID 集合
     */
    private final Set<String> permissionIds = new HashSet<>();
    /**
     * 角色名称
     */
    private String roleName;
    /**
     * 角色描述
     */
    private String description;
    /**
     * 状态
     */
    private Status status;

    public Role(String roleId, String roleCode, String roleName, String description, Status status) {
        if (StrKit.isBlank(roleId)) {
            throw new IllegalArgumentException("roleId must not be blank");
        }
        if (StrKit.isBlank(roleCode)) {
            throw new IllegalArgumentException("roleCode must not be blank");
        }
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.status = Objects.requireNonNullElse(status, Status.ENABLED);
        this.createdAt = Instant.now();
    }

    /**
     * 重命名/描述修改。
     */
    public void rename(String roleName, String description) {
        if (StrKit.isNotBlank(roleName)) {
            this.roleName = roleName;
        }
        if (description != null) {
            this.description = description;
        }
    }

    /**
     * 启用。
     */
    public void enable() {
        this.status = Status.ENABLED;
    }

    /**
     * 禁用。
     */
    public void disable() {
        this.status = Status.DISABLED;
    }

    /**
     * 分配权限给角色。
     */
    public void assignPermissions(Set<String> newPermissionIds) {
        if (newPermissionIds != null) {
            this.permissionIds.clear();
            this.permissionIds.addAll(newPermissionIds);
        }
    }

    /**
     * 添加单个权限。
     */
    public void addPermission(String permissionId) {
        if (StrKit.isNotBlank(permissionId)) {
            this.permissionIds.add(permissionId);
        }
    }

    /**
     * 移除权限。
     */
    public void removePermission(String permissionId) {
        this.permissionIds.remove(permissionId);
    }

    @Override
    public String id() {
        return roleId;
    }

    /**
     * 角色状态。
     */
    public enum Status {
        ENABLED, DISABLED
    }
}