package io.ddd4j.sample.javalin.satoken.rbac;

import java.util.Collections;
import io.ddd4j.sample.javalin.satoken.rbac.application.RbacService;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.User;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.UserRepository;

import java.util.HashSet;
import java.util.List;

/**
 * RBAC 演示数据初始化：与外部 Boot 示例 {@code ddd4j-boot-sample-auth-satoken} 使用同一 Subject 鉴权语义。
 *
 * <p>Javalin + Guice 没有 Spring {@code @PostConstruct} 钩子，故显式提供
 * {@link #initRbacData(UserRepository, RoleRepository, PermissionRepository)}
 * 方法在应用启动时调用。
 *
 * <p>预置 3 用户 + 3 角色 + 6 权限，与 Spring 示例数据完全一致：
 * <ul>
 *   <li>用户：admin(10001) / user(10002) / disabled(10003)</li>
 *   <li>角色：admin / user / manager</li>
 *   <li>权限：user:add / user:delete / user:list / role:add / goods:view / order:pay</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class RbacConfig {

    private RbacConfig() {
    }

    /**
     * 预置 RBAC 演示数据。
     */
    public static void initRbacData(UserRepository userRepository,
                                    RoleRepository roleRepository,
                                    PermissionRepository permissionRepository) {
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
        User admin = new User("10001", "admin", "admin", "管理员", User.Status.ENABLED);
        admin.assignRoles(new HashSet<>(Collections.singletonList("R001")));

        User user = new User("10002", "user", "user", "张三", User.Status.ENABLED);
        user.assignRoles(new HashSet<>(Collections.singletonList("R002")));

        User disabled = new User("10003", "disabled", "disabled", "李四（已禁用）", User.Status.DISABLED);
        disabled.assignRoles(new HashSet<>(Collections.singletonList("R002")));

        userRepository.save(admin);
        userRepository.save(user);
        userRepository.save(disabled);
    }

    /**
     * 注册权限数据源：直接使用 {@link RbacService}（其自身实现了
     * {@link io.ddd4j.core.subject.SubjectDataProvider}）。
     */
    public static RbacService subjectDataProvider(RbacService rbacService) {
        return rbacService;
    }

}
