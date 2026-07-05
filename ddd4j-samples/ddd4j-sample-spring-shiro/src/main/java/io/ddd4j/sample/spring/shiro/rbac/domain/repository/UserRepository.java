package io.ddd4j.sample.spring.shiro.rbac.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.User;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口（RBAC）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface UserRepository extends Repository<User, String> {

    /**
     * 查询全部用户。
     */
    List<User> findAll();

    /**
     * 按用户名查找。
     */
    Optional<User> findByUsername(String username);

    /**
     * 按状态过滤查询用户列表。
     */
    List<User> findByStatus(User.Status status);
}