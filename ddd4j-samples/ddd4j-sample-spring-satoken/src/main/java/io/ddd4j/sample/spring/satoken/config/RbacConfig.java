package io.ddd4j.sample.spring.satoken.config;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.spring.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.spring.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.spring.satoken.rbac.domain.model.User;
import io.ddd4j.sample.spring.satoken.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.spring.satoken.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.spring.satoken.rbac.domain.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 鉴权配置：注册权限数据源（SubjectDataProvider）+ 预置演示数据。
 *
 * <p>这是 ddd4j-auth 的核心用法——业务实现 SubjectDataProvider 提供权限/角色数据源，
 * 框架不持有权限数据，保证三种鉴权框架行为一致。
 *
 * <p>实际生产中预置数据来自 SQL 初始化脚本或管理后台，本示例在内存中预置 3 用户 + 3 角色 + 6 权限。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
public class RbacConfig {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RbacConfig(UserRepository userRepository,
                      RoleRepository roleRepository,
                      PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    /**
     * 预置 RBAC 演示数据。
     *
     * <p>3 用户：admin(10001) / user(10002) / disabled(10003)
     * <br>3 角色：admin / user / manager
     * <br>6 权限：user:add / user:delete / user:list / role:add / goods:view / order:pay
     */
    @PostConstruct
    public void initRbacData() {
        // 1) 创建权限
        Permission pUserAdd = new Permission("P001", "user:add", "新增用户", "user", Permission.Status.ENABLED);
        Permission pUserDelete = new Permission("P002", "user:delete", "删除用户", "user", Permission.Status.ENABLED);
        Permission pUserList = new Permission("P003", "user:list", "查询用户", "user", Permission.Status.ENABLED);
        Permission pRoleAdd = new Permission("P004", "role:add", "新增角色", "role", Permission.Status.ENABLED);
        Permission pGoodsView = new Permission("P005", "goods:view", "查看商品", "goods", Permission.Status.ENABLED);
        Permission pOrderPay = new Permission("P006", "order:pay", "订单支付", "order", Permission.Status.ENABLED);

        permissionRepository.save(pUserAdd);
        permissionRepository.save(pUserDelete);
        permissionRepository.save(pUserList);
        permissionRepository.save(pRoleAdd);
        permissionRepository.save(pGoodsView);
        permissionRepository.save(pOrderPay);

        // 2) 创建角色并分配权限
        Role adminRole = new Role("R001", "admin", "超级管理员", "拥有全部权限", Role.Status.ENABLED);
        adminRole.assignPermissions(new HashSet<>(List.of("P001", "P002", "P003", "P004", "P005", "P006")));

        Role userRole = new Role("R002", "user", "普通用户", "基础用户角色", Role.Status.ENABLED);
        userRole.assignPermissions(new HashSet<>(List.of("P003", "P005")));

        Role managerRole = new Role("R003", "manager", "业务管理员", "管理商品和订单", Role.Status.ENABLED);
        managerRole.assignPermissions(new HashSet<>(List.of("P003", "P005", "P006")));

        roleRepository.save(adminRole);
        roleRepository.save(userRole);
        roleRepository.save(managerRole);

        // 3) 创建用户并分配角色
        User admin = new User("10001", "admin", "123456", "管理员", User.Status.ENABLED);
        admin.assignRoles(new HashSet<>(List.of("R001")));

        User user = new User("10002", "user", "123456", "张三", User.Status.ENABLED);
        user.assignRoles(new HashSet<>(List.of("R002")));

        User disabled = new User("10003", "disabled", "123456", "李四（已禁用）", User.Status.DISABLED);
        disabled.assignRoles(new HashSet<>(List.of("R002")));

        userRepository.save(admin);
        userRepository.save(user);
        userRepository.save(disabled);
    }

    /**
     * 注册权限数据源：从 RBAC 存储中读取用户的角色和权限。
     *
     * <p>关键演示：业务实现 SubjectDataProvider，框架不感知具体数据来源。
     */
    @Bean
    public SubjectDataProvider subjectDataProvider() {
        return new SubjectDataProvider() {

            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                Object userId = principal.getUserId();
                if (userId == null) {
                    return List.of();
                }
                return userRepository.findById(String.valueOf(userId))
                        .map(user -> {
                            Set<String> permissions = new HashSet<>();
                            for (String roleId : user.getRoleIds()) {
                                roleRepository.findById(roleId).ifPresent(role ->
                                        permissions.addAll(role.getPermissionIds()));
                            }
                            // 权限 ID -> 权限 Code（用于 SubjectKit.hasPermission 校验）
                            return permissions.stream()
                                    .map(pid -> permissionRepository.findById(pid)
                                            .map(Permission::getPermissionCode).orElse(null))
                                    .filter(java.util.Objects::nonNull)
                                    .collect(Collectors.toList());
                        })
                        .orElse(List.of());
            }

            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                Object userId = principal.getUserId();
                if (userId == null) {
                    return List.of();
                }
                return userRepository.findById(String.valueOf(userId))
                        .map(user -> user.getRoleIds().stream()
                                .map(rid -> roleRepository.findById(rid)
                                        .map(Role::getRoleCode).orElse(null))
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toList()))
                        .orElse(List.of());
            }

            @Override
            public boolean isDisabled(Object loginId, String service) {
                if (loginId == null) {
                    return false;
                }
                return userRepository.findById(String.valueOf(loginId))
                        .map(u -> u.getStatus() == User.Status.DISABLED)
                        .orElse(false);
            }
        };
    }

}