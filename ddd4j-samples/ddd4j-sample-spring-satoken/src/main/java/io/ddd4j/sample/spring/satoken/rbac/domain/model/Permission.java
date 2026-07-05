package io.ddd4j.sample.spring.satoken.rbac.domain.model;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * 权限聚合根（RBAC 模型）。
 *
 * <p>权限是最小的授权单位，按 {@code module} 分组。
 * 例如：{@code user:add} / {@code user:delete} / {@code goods:view} / {@code order:pay}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public class Permission extends AggregateRoot<String> {

    private static final long serialVersionUID = 1L;

    /** 权限 ID（与 permissionCode 一致，简化示例） */
    private final String permissionId;
    /** 权限编码（如 {@code user:add}） */
    private final String permissionCode;
    /** 权限名称 */
    private String permissionName;
    /** 所属模块（用于权限分组） */
    private String module;
    /** 状态 */
    private Status status;
    /** 创建时间 */
    private final Instant createdAt;

    public Permission(String permissionId, String permissionCode, String permissionName, String module, Status status) {
        if (StrKit.isBlank(permissionId)) {
            throw new IllegalArgumentException("permissionId must not be blank");
        }
        if (StrKit.isBlank(permissionCode)) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
        this.permissionId = permissionId;
        this.permissionCode = permissionCode;
        this.permissionName = permissionName;
        this.module = module;
        this.status = Objects.requireNonNullElse(status, Status.ENABLED);
        this.createdAt = Instant.now();
    }

    /**
     * 重命名。
     */
    public void rename(String permissionName, String module) {
        if (StrKit.isNotBlank(permissionName)) {
            this.permissionName = permissionName;
        }
        if (StrKit.isNotBlank(module)) {
            this.module = module;
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

    @Override
    public String id() {
        return permissionId;
    }

    /**
     * 权限状态。
     */
    public enum Status {
        ENABLED, DISABLED
    }
}