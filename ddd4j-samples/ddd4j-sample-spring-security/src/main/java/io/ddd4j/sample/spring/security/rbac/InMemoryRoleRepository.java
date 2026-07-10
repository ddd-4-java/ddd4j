package io.ddd4j.sample.spring.security.rbac;

import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色内存仓储。
 *
 * <p>按 code 索引；{@link #findByPermissionCode(String)} 用于反查拥有某权限的所有角色。
 *
 * <p>本类在所有示例（Spring/Security × ddd4j）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Repository
public class InMemoryRoleRepository {

    private final Map<String, Role> rolesByCode = new ConcurrentHashMap<>();

    public Role save(Role role) {
        if (role.getCode() == null || role.getCode().isBlank()) {
            throw new IllegalArgumentException("role code must not be blank");
        }
        rolesByCode.put(role.getCode(), role);
        return role;
    }

    public Optional<Role> findByCode(String code) {
        return Optional.ofNullable(rolesByCode.get(code));
    }

    public List<Role> findAll() {
        return new ArrayList<>(rolesByCode.values());
    }

    public Collection<Role> findByPermissionCode(String permissionCode) {
        List<Role> result = new ArrayList<>();
        for (Role r : rolesByCode.values()) {
            if (r.getPermissionCodes().contains(permissionCode)) {
                result.add(r);
            }
        }
        return result;
    }

    public boolean deleteByCode(String code) {
        return rolesByCode.remove(code) != null;
    }

    public boolean existsByCode(String code) {
        return rolesByCode.containsKey(code);
    }

}