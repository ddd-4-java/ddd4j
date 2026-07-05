package io.ddd4j.sample.spring.satoken.rbac.infrastructure;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.spring.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.spring.satoken.rbac.domain.repository.PermissionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版权限仓储（演示用）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Repository
public class InMemoryPermissionRepository implements PermissionRepository {

    private final ConcurrentMap<String, PermissionRow> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<Permission> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(InMemoryPermissionRepository::toModel);
    }

    @Override
    public Optional<Permission> findByPermissionCode(String permissionCode) {
        if (StrKit.isBlank(permissionCode)) {
            return Optional.empty();
        }
        return rows.values().stream()
                .filter(r -> Objects.equals(permissionCode, r.permissionCode))
                .findFirst()
                .map(InMemoryPermissionRepository::toModel);
    }

    @Override
    public List<Permission> findByModule(String module) {
        if (StrKit.isBlank(module)) {
            return List.of();
        }
        return rows.values().stream()
                .filter(r -> Objects.equals(module, r.module))
                .map(InMemoryPermissionRepository::toModel)
                .toList();
    }

    @Override
    public List<Permission> findAll() {
        return rows.values().stream()
                .map(InMemoryPermissionRepository::toModel)
                .toList();
    }

    @Override
    public Permission save(Permission aggregate) {
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

    private static PermissionRow toRow(Permission permission) {
        PermissionRow row = new PermissionRow();
        row.permissionId = permission.id();
        row.permissionCode = permission.getPermissionCode();
        row.permissionName = permission.getPermissionName();
        row.module = permission.getModule();
        row.status = permission.getStatus().name();
        return row;
    }

    private static Permission toModel(PermissionRow row) {
        return new Permission(row.permissionId, row.permissionCode, row.permissionName, row.module,
                Permission.Status.valueOf(row.status));
    }

    /**
     * 权限内部持久化行。
     */
    static class PermissionRow {

        String permissionId;
        String permissionCode;
        String permissionName;
        String module;
        String status;
    }
}