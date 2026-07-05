package io.ddd4j.sample.quarkus.satoken.rbac;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限内存仓储。
 *
 * <p>使用 {@link ApplicationScoped} 暴露为 CDI 单例 Bean；底层为 {@link ConcurrentHashMap}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class InMemoryPermissionRepository {

    private final ConcurrentHashMap<String, Permission> store = new ConcurrentHashMap<>();

    /**
     * 保存或覆盖权限。
     */
    public Permission save(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        store.put(permission.getCode(), permission);
        return permission;
    }

    /**
     * 按编码查询。
     */
    public Optional<Permission> findByCode(String code) {
        return Optional.ofNullable(store.get(code));
    }

    /**
     * 查询全部。
     */
    public List<Permission> findAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * 删除权限。
     */
    public boolean deleteByCode(String code) {
        return store.remove(code) != null;
    }

    /**
     * 当前存储的权限数量。
     */
    public int size() {
        return store.size();
    }

}