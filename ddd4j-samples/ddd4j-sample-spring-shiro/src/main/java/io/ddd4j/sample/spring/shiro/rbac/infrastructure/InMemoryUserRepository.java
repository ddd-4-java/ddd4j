package io.ddd4j.sample.spring.shiro.rbac.infrastructure;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.User;
import io.ddd4j.sample.spring.shiro.rbac.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版用户仓储（演示用）。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储用户的字段映射。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

    /** 用户 ID -> 用户行 */
    private final ConcurrentMap<String, UserRow> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(InMemoryUserRepository::toModel);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (StrKit.isBlank(username)) {
            return Optional.empty();
        }
        return rows.values().stream()
                .filter(r -> Objects.equals(username, r.username))
                .findFirst()
                .map(InMemoryUserRepository::toModel);
    }

    @Override
    public List<User> findByStatus(User.Status status) {
        return rows.values().stream()
                .filter(r -> Objects.equals(status.name(), r.status))
                .map(InMemoryUserRepository::toModel)
                .toList();
    }

    @Override
    public List<User> findAll() {
        return rows.values().stream()
                .map(InMemoryUserRepository::toModel)
                .toList();
    }

    @Override
    public User save(User aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        rows.put(aggregate.id(), toRow(aggregate));
        return aggregate;
    }

    @Override
    public void deleteById(String id) {
        if (StrKit.isNotBlank(id)) {
            rows.remove(id);
        }
    }

    // ============================ 模型与行转换 ============================

    private static UserRow toRow(User user) {
        UserRow row = new UserRow();
        row.userId = user.id();
        row.username = user.getUsername();
        row.password = user.getPassword();
        row.realName = user.getRealName();
        row.status = user.getStatus().name();
        row.roleIds = new ArrayList<>(user.getRoleIds());
        return row;
    }

    private static User toModel(UserRow row) {
        User user = new User(row.userId, row.username, row.password, row.realName, User.Status.valueOf(row.status));
        if (row.roleIds != null && !row.roleIds.isEmpty()) {
            user.assignRoles(new java.util.HashSet<>(row.roleIds));
        }
        return user;
    }

    /**
     * 用户内部持久化行。
     */
    static class UserRow {

        String userId;
        String username;
        String password;
        String realName;
        String status;
        List<String> roleIds;
    }
}