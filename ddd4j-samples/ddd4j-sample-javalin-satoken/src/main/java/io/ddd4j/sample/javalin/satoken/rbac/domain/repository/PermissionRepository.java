package io.ddd4j.sample.javalin.satoken.rbac.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Permission;

import java.util.List;
import java.util.Optional;

/**
 * 权限仓储接口（RBAC）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface PermissionRepository extends Repository<Permission, String> {

    /**
     * 查询全部权限。
     */
    List<Permission> findAll();

    /**
     * 按权限编码查找。
     */
    Optional<Permission> findByPermissionCode(String permissionCode);

    /**
     * 按模块查询权限列表。
     */
    List<Permission> findByModule(String module);
}