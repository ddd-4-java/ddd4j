package io.ddd4j.sample.quarkus.shiro.rbac;

import io.ddd4j.kit.lang.CollKit;

import java.util.Objects;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RBAC 配置：初始化内存数据 + 注册 SubjectDataProvider。
 *
 * <p>演示启动期种子数据：
 * <ul>
 *   <li>权限：user:list / user:add / user:delete / order:pay / order:cancel / admin:*</li>
 *   <li>角色：admin（全部权限）/ user（user:list）</li>
 *   <li>用户：admin/123456、user/123456、disabled/123456（disabled=true）</li>
 * </ul>
 *
 * <p>{@link SubjectDataProvider} 从 RBAC 仓储实时聚合用户角色/权限，用于 Shiro 注解校验。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class RbacConfig {

    @Inject
    InMemoryUserRepository userRepository;

    @Inject
    InMemoryRoleRepository roleRepository;

    @Inject
    InMemoryPermissionRepository permissionRepository;

    /**
     * 应用启动时初始化演示数据。
     */
    void onStart(@Observes StartupEvent event) {
        reset();
    }

    /**
     * 重建样例的确定性 RBAC 金标数据。
     */
    public synchronized void reset() {
        userRepository.clear();
        roleRepository.clear();
        permissionRepository.clear();

        // 1. 权限
        permissionRepository.save(new Permission("user:list", "用户列表", "查询用户列表"));
        permissionRepository.save(new Permission("user:add", "新增用户", "新增用户"));
        permissionRepository.save(new Permission("user:delete", "删除用户", "删除用户"));
        permissionRepository.save(new Permission("order:pay", "支付订单", "支付订单"));
        permissionRepository.save(new Permission("order:cancel", "取消订单", "取消订单"));
        permissionRepository.save(new Permission("admin:dashboard", "管理面板", "访问管理面板"));

        // 2. 角色（绑定权限）
        Role admin = new Role("admin", "管理员", "拥有全部权限", new HashSet<>(List.of(
                "user:list", "user:add", "user:delete",
                "order:pay", "order:cancel", "admin:dashboard")));
        roleRepository.save(admin);

        Role user = new Role("user", "普通用户", "基础用户", new HashSet<>(List.of("user:list")));
        roleRepository.save(user);

        // 3. 用户（绑定角色）
        userRepository.save(new User("u-admin", "admin", "管理员", "123456",
                new HashSet<>(Set.of("admin", "user")), false));
        userRepository.save(new User("u-user", "user", "普通用户", "123456",
                new HashSet<>(Set.of("user")), false));
        userRepository.save(new User("u-disabled", "disabled", "已禁用用户", "123456",
                new HashSet<>(Set.of("user")), true));
    }

    /**
     * RBAC 权限数据源：从 User -> Role -> Permission 实时聚合。
     */
    @Produces
    @Singleton
    public SubjectDataProvider subjectDataProvider() {
        return new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                if (Objects.isNull(principal) || Objects.isNull(principal.getUserId())) {
                    return List.of();
                }
                String userId = String.valueOf(principal.getUserId());
                return userRepository.findById(userId)
                        .map(user -> aggregatePermissions(user.getRoleCodes()))
                        .orElse(List.of());
            }

            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                if (Objects.isNull(principal) || Objects.isNull(principal.getUserId())) {
                    return List.of();
                }
                String userId = String.valueOf(principal.getUserId());
                return userRepository.findById(userId)
                        .map(user -> List.copyOf(user.getRoleCodes()))
                        .orElse(List.of());
            }
        };
    }

    /**
     * 聚合多角色下的全部权限编码（去重）。
     */
    private List<String> aggregatePermissions(Set<String> roleCodes) {
        if (CollKit.isEmpty(roleCodes)) {
            return List.of();
        }
        Set<String> aggregated = new HashSet<>();
        for (String roleCode : roleCodes) {
            roleRepository.findByCode(roleCode)
                    .ifPresent(role -> {
                        if (Objects.nonNull(role.getPermissionCodes())) {
                            aggregated.addAll(role.getPermissionCodes());
                        }
                    });
        }
        return new ArrayList<>(aggregated);
    }

}
