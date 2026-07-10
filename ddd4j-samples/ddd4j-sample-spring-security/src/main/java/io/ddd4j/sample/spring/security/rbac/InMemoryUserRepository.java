package io.ddd4j.sample.spring.security.rbac;

import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户内存仓储。
 *
 * <p>按 ID 索引；同时维护 {@code username → id} 反向索引用于登录。
 *
 * <p>本类在所有示例（Spring/Security × ddd4j）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Repository
public class InMemoryUserRepository {

    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, String> idByUsername = new ConcurrentHashMap<>();

    public User save(User user) {
        if (user.getId() == null) {
            user.setId("u-" + System.currentTimeMillis());
        }
        usersById.put(user.getId(), user);
        if (user.getUsername() != null) {
            idByUsername.put(user.getUsername(), user.getId());
        }
        return user;
    }

    public Optional<User> findById(String id) {
        return Optional.ofNullable(usersById.get(id));
    }

    public Optional<User> findByUsername(String username) {
        String id = idByUsername.get(username);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersById.get(id));
    }

    public List<User> findAll() {
        return new ArrayList<>(usersById.values());
    }

    public Collection<User> findByRoleCode(String roleCode) {
        List<User> result = new ArrayList<>();
        for (User u : usersById.values()) {
            if (u.getRoleCodes().contains(roleCode)) {
                result.add(u);
            }
        }
        return result;
    }

    public boolean deleteById(String id) {
        User removed = usersById.remove(id);
        if (removed != null && removed.getUsername() != null) {
            idByUsername.remove(removed.getUsername());
            return true;
        }
        return false;
    }

    public boolean existsById(String id) {
        return usersById.containsKey(id);
    }

    public boolean existsByUsername(String username) {
        return idByUsername.containsKey(username);
    }

}