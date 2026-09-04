package io.ddd4j.sample.javalin.satoken.rbac.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Role;

import java.util.List;
import java.util.Optional;

/**
 * 角色仓储接口（RBAC）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface RoleRepository extends Repository<Role, String> {

    /**
     * 查询全部角色。
     */
    List<Role> findAll();

    /**
     * 按角色编码查找。
     */
    Optional<Role> findByRoleCode(String roleCode);
}