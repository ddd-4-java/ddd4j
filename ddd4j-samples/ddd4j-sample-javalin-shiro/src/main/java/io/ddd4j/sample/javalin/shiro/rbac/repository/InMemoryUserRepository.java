package io.ddd4j.sample.javalin.shiro.rbac.repository;

import io.ddd4j.sample.javalin.shiro.rbac.domain.User;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 用户仓储：内存实现。
 *
 * <p>仅用于演示 RBAC 完整 CRUD 流程。生产环境应替换为 JDBC / MyBatis / JPA 实现。
 *
 * <p>本类在所有 7 个示例（Spring/Quarkus/Javalin × Sa-Token/Shiro/Security）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryUserRepository {

    private final ConcurrentMap<String, User> store = new ConcurrentHashMap<>();

    /**
     * 新增或更新用户。
     */
    public User save(User user) {
        store.put(user.loginId(), user);
        return user;
    }

    /**
     * 按 loginId 查询用户。
     */
    public Optional<User> findByLoginId(String loginId) {
        return Optional.ofNullable(store.get(loginId));
    }

    /**
     * 按 loginId 删除用户。
     */
    public boolean deleteByLoginId(String loginId) {
        return store.remove(loginId) != null;
    }

    /**
     * 查询全部用户。
     */
    public Collection<User> findAll() {
        return Collections.unmodifiableCollection(store.values());
    }

    /**
     * 当前用户数量。
     */
    public int count() {
        return store.size();
    }

}