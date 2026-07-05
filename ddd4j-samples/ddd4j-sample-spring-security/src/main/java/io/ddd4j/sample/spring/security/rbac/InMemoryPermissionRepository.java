package io.ddd4j.sample.spring.security.rbac;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限内存仓储。
 *
 * <p>按 code 索引；权限一旦创建不可修改 code，只能修改 name/description。
 *
 * <p>本类在所有示例（Spring/Security × ddd4j）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Repository
public class InMemoryPermissionRepository {

    private final Map<String, Permission> permissionsByCode = new ConcurrentHashMap<>();

    public Permission save(Permission permission) {
        if (permission.getCode() == null || permission.getCode().isBlank()) {
            throw new IllegalArgumentException("permission code must not be blank");
        }
        permissionsByCode.put(permission.getCode(), permission);
        return permission;
    }

    public Optional<Permission> findByCode(String code) {
        return Optional.ofNullable(permissionsByCode.get(code));
    }

    public List<Permission> findAll() {
        return new ArrayList<>(permissionsByCode.values());
    }

    public boolean deleteByCode(String code) {
        return permissionsByCode.remove(code) != null;
    }

    public boolean existsByCode(String code) {
        return permissionsByCode.containsKey(code);
    }

}