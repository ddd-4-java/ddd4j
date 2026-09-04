package io.ddd4j.sample.javalin.shiro.rbac;

import java.util.Objects;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.javalin.shiro.rbac.domain.Role;
import io.ddd4j.sample.javalin.shiro.rbac.domain.User;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RBAC 初始化配置：演示账号/角色/权限种子数据 + 注册 SubjectDataProvider。
 *
 * <p>本类与 {@code ddd4j-sample-javalin-satoken} 的 RBAC 业务代码<b>完全一致</b>，
 * 唯一区别是：本类在 {@link #createSubjectDataProvider()} 中返回的是基于 {@link RbacService} 派生的
 * SubjectDataProvider（业务实现细节对所有示例相同）；不同框架（Sa-Token/Shiro）的差异在于
 * 启动器调用方式，不在本类中体现。
 *
 * <h3>演示账号</h3>
 * <ul>
 *   <li>admin / admin123：角色=admin，权限=user:*, order:*</li>
 *   <li>zhangsan / pass123：角色=user，权限=user:list, order:create, order:pay</li>
 *   <li>lisi / pass123：角色=user，权限=user:list, order:create</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class RbacConfig {

    private RbacConfig() {
    }

    /**
     * 初始化 RBAC 内存数据：种子账号 / 角色 / 权限。
     *
     * <p>业务侧启动时调用本方法，完成种子数据落库。
     */
    public static void initSeedData(RbacService rbacService) {
        // ==================== 种子角色 ====================
        // admin：拥有全部权限
        rbacService.saveRole(new Role("admin", "管理员",
                Set.of("user:list", "user:add", "user:update", "user:delete",
                        "order:create", "order:pay", "order:ship", "order:cancel",
                        "permission:list", "role:list")));
        // user：拥有基本业务权限
        rbacService.saveRole(new Role("user", "普通用户",
                Set.of("user:list", "order:create", "order:pay")));

        // ==================== 种子用户 ====================
        // admin：admin 角色
        rbacService.saveUser(new User("admin", "admin123", "管理员",
                Set.of("admin"), Set.of()));
        // zhangsan：user 角色（业务授权：直接拥有 order:pay）
        rbacService.saveUser(new User("zhangsan", "pass123", "张三",
                Set.of("user"), Set.of("order:pay")));
        // lisi：user 角色（无额外权限）
        rbacService.saveUser(new User("lisi", "pass123", "李四",
                Set.of("user"), Set.of()));
    }

    /**
     * 注册 SubjectDataProvider：业务侧从 RBAC 仓储派生权限码 / 角色列表。
     *
     * <p>本方法是 RBAC 业务侧与鉴权框架的桥梁：
     * ddd4j-auth-shiro 的 {@code ShiroSubject.isPermitted/hasRole} 会委托此 SPI。
     */
    public static SubjectDataProvider createSubjectDataProvider(
            InMemoryUserRepository userRepository,
            InMemoryRoleRepository roleRepository) {
        return new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                if (Objects.isNull(principal)) {
                    return List.of();
                }
                Object loginId = principal.getLoginId();
                if (Objects.isNull(loginId)) {
                    return List.of();
                }
                return userRepository.findByLoginId(String.valueOf(loginId))
                        .map(rbacServicePerms(roleRepository))
                        .orElse(List.of());
            }

            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                if (Objects.isNull(principal)) {
                    return List.of();
                }
                Object loginId = principal.getLoginId();
                if (Objects.isNull(loginId)) {
                    return List.of();
                }
                return userRepository.findByLoginId(String.valueOf(loginId))
                        .map(User::roles)
                        .map(roles -> (List<String>) List.copyOf(roles))
                        .orElse(List.of());
            }
        };
    }

    /**
     * 派生用户最终权限码：用户直接权限 ∪ 角色持有的权限。
     */
    private static java.util.function.Function<User, List<String>> rbacServicePerms(InMemoryRoleRepository roleRepository) {
        return user -> {
            Set<String> allPerms = new HashSet<>(user.permissions());
            for (String roleCode : user.roles()) {
                roleRepository.findByCode(roleCode)
                        .ifPresent(role -> allPerms.addAll(role.permissions()));
            }
            return List.copyOf(allPerms);
        };
    }

}