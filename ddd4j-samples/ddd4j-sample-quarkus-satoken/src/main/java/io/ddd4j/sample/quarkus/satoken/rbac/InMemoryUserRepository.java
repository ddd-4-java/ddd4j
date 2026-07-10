package io.ddd4j.sample.quarkus.satoken.rbac;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户内存仓储。
 *
 * <p>使用 {@link ApplicationScoped} 暴露为 CDI 单例 Bean；底层为 {@link ConcurrentHashMap}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class InMemoryUserRepository {

    private final ConcurrentHashMap<String, User> store = new ConcurrentHashMap<>();

    /**
     * 保存或覆盖用户。
     */
    public User save(User user) {
        Objects.requireNonNull(user, "user");
        store.put(user.getId(), user);
        return user;
    }

    /**
     * 按 ID 查询。
     */
    public Optional<User> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * 按登录名查询。
     */
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(u -> username.equals(u.getUsername()))
                .findFirst();
    }

    /**
     * 查询全部。
     */
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * 删除用户。
     */
    public boolean deleteById(String id) {
        return store.remove(id) != null;
    }

    /**
     * 为用户绑定角色编码。
     */
    public User addRole(String userId, String roleCode) {
        User user = store.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found: " + userId);
        }
        if (user.getRoleCodes() == null) {
            user.setRoleCodes(new HashSet<>());
        }
        user.getRoleCodes().add(roleCode);
        return user;
    }

    /**
     * 为用户解除角色。
     */
    public User removeRole(String userId, String roleCode) {
        User user = store.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found: " + userId);
        }
        if (user.getRoleCodes() != null) {
            user.getRoleCodes().remove(roleCode);
        }
        return user;
    }

    /**
     * 批量查询：根据角色编码聚合用户。
     */
    public List<User> findByRoleCode(String roleCode) {
        List<User> result = new ArrayList<>();
        for (User user : store.values()) {
            Set<String> codes = user.getRoleCodes();
            if (codes != null && codes.contains(roleCode)) {
                result.add(user);
            }
        }
        return result;
    }

    /**
     * 当前存储的用户数量。
     */
    public int size() {
        return store.size();
    }

    /**
     * 当前存储的全部用户（只读视图）。
     */
    public Collection<User> values() {
        return store.values();
    }

}