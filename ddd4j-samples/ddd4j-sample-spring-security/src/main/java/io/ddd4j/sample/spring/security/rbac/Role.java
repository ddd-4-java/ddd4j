package io.ddd4j.sample.spring.security.rbac;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * RBAC 角色实体。
 *
 * <p>角色是用户与权限之间的桥梁：
 * <ul>
 *   <li>用户 ⇢ 角色（多对多）</li>
 *   <li>角色 ⇢ 权限（多对多）</li>
 * </ul>
 *
 * <p>Spring Security 中 {@code hasRole('admin')} 会自动加上 {@code ROLE_} 前缀，
 * 因此角色代码建议使用纯单词（如 {@code admin}、{@code user}）。
 *
 * <p>本类在所有示例（Spring/Security × ddd4j）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Role {

    private String code;
    private String name;
    private String description;
    private Set<String> permissionCodes = new HashSet<>();

    public Role() {
    }

    public Role(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Role(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public Role setCode(String code) {
        this.code = code;
        return this;
    }

    public String getName() {
        return name;
    }

    public Role setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Role setDescription(String description) {
        this.description = description;
        return this;
    }

    public Set<String> getPermissionCodes() {
        return permissionCodes;
    }

    public Role setPermissionCodes(Set<String> permissionCodes) {
        this.permissionCodes = permissionCodes;
        return this;
    }

    public Role addPermission(String permissionCode) {
        this.permissionCodes.add(permissionCode);
        return this;
    }

    public Role removePermission(String permissionCode) {
        this.permissionCodes.remove(permissionCode);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role role)) return false;
        return Objects.equals(code, role.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

}