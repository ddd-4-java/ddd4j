package io.ddd4j.sample.javalin.shiro.rbac.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * RBAC 角色聚合根。
 *
 * <p>角色是用户与权限之间的桥梁：一组权限的命名集合（如 {@code admin} / {@code user}）。
 * 用户通过被赋予角色而获得该角色持有的所有权限码。
 *
 * <p>本类在所有 7 个示例（Spring/Quarkus/Javalin × Sa-Token/Shiro/Security）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Role {

    /**
     * 角色编码（全局唯一，如 {@code admin} / {@code user}）
     */
    private final String code;
    /**
     * 角色名称（可选展示名）
     */
    private final String name;
    /**
     * 角色持有的权限码集合
     */
    private final Set<String> permissions;

    public Role(String code) {
        this(code, null, Collections.emptySet());
    }

    public Role(String code, String name) {
        this(code, name, Collections.emptySet());
    }

    public Role(String code, String name, Set<String> permissions) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = name;
        this.permissions = Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
    }

    /**
     * 工厂方法：构造角色并附带权限码。
     *
     * @param code        角色编码
     * @param permissions 权限码列表
     * @return 角色实例
     */
    public static Role of(String code, String... permissions) {
        Set<String> set = new LinkedHashSet<>();
        Collections.addAll(set, permissions);
        return new Role(code, null, set);
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public Set<String> permissions() {
        return permissions;
    }

    /**
     * 判定本角色是否包含指定权限码。
     *
     * @param permissionCode 权限码
     * @return true-包含，false-不包含
     */
    public boolean hasPermission(String permissionCode) {
        return permissions.contains(permissionCode) || permissions.contains("*");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Role)) {
            return false;
        }
        Role role = (Role) o;
        return Objects.equals(code, role.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return "Role{" + code + ", perms=" + permissions + "}";
    }

}