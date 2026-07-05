package io.ddd4j.sample.spring.security.rbac;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * RBAC 用户实体。
 *
 * <p>持有登录账号、密码（明文，仅供 InMemory 演示）及关联的角色集合。
 * 用户-角色是多对多：{@link #roleCodes}。
 *
 * <p>本类在所有示例（Spring/Security × ddd4j）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class User {

    private String id;
    private String username;
    private String password;
    private boolean enabled = true;
    private Set<String> roleCodes = new HashSet<>();

    public User() {
    }

    public User(String id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public User setId(String id) {
        this.id = id;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public User setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public User setPassword(String password) {
        this.password = password;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public User setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Set<String> getRoleCodes() {
        return roleCodes;
    }

    public User setRoleCodes(Set<String> roleCodes) {
        this.roleCodes = roleCodes;
        return this;
    }

    public User addRole(String roleCode) {
        this.roleCodes.add(roleCode);
        return this;
    }

    public User removeRole(String roleCode) {
        this.roleCodes.remove(roleCode);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}