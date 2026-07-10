package io.ddd4j.sample.quarkus.satoken.rbac;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色内存仓储。
 *
 * <p>使用 {@link ApplicationScoped} 暴露为 CDI 单例 Bean；底层为 {@link ConcurrentHashMap}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class InMemoryRoleRepository {

    private final ConcurrentHashMap<String, Role> store = new ConcurrentHashMap<>();

    /**
     * 保存或覆盖角色。
     */
    public Role save(Role role) {
        Objects.requireNonNull(role, "role");
        store.put(role.getCode(), role);
        return role;
    }

    /**
     * 按编码查询。
     */
    public Optional<Role> findByCode(String code) {
        return Optional.ofNullable(store.get(code));
    }

    /**
     * 查询全部。
     */
    public List<Role> findAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * 删除角色。
     */
    public boolean deleteByCode(String code) {
        return store.remove(code) != null;
    }

    /**
     * 为角色绑定权限编码。
     */
    public Role addPermission(String roleCode, String permissionCode) {
        Role role = store.get(roleCode);
        if (role == null) {
            throw new IllegalArgumentException("role not found: " + roleCode);
        }
        if (role.getPermissionCodes() == null) {
            role.setPermissionCodes(new HashSet<>());
        }
        role.getPermissionCodes().add(permissionCode);
        return role;
    }

    /**
     * 为角色解除权限。
     */
    public Role removePermission(String roleCode, String permissionCode) {
        Role role = store.get(roleCode);
        if (role == null) {
            throw new IllegalArgumentException("role not found: " + roleCode);
        }
        if (role.getPermissionCodes() != null) {
            role.getPermissionCodes().remove(permissionCode);
        }
        return role;
    }

    /**
     * 当前存储的角色数量。
     */
    public int size() {
        return store.size();
    }

    /**
     * 全部角色编码集合（用于 SubjectDataProvider 角色回填）。
     */
    public Set<String> codes() {
        return new HashSet<>(store.keySet());
    }

}