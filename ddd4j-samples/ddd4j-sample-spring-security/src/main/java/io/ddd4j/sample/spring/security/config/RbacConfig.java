package io.ddd4j.sample.spring.security.config;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.spring.security.rbac.*;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * RBAC 初始化配置 + 权限数据源（SubjectDataProvider）注册。
 *
 * <p>启动时通过 {@link PostConstruct} 注入演示数据（2 角色 / 5 权限 / 2 用户）。
 *
 * <p>保留 ddd4j-auth 的 {@link SubjectDataProvider} 注册逻辑，
 * 让 ddd4j 的 Subject 抽象与 Spring Security 解耦——业务代码切换鉴权框架时无需改动。
 *
 * <p>Spring Security 的具体配置（{@code SecurityFilterChain}、{@code UserDetailsService}）
 * 在 {@link SecurityConfig} 中定义。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
public class RbacConfig {

    private final InMemoryUserRepository userRepository;
    private final InMemoryRoleRepository roleRepository;
    private final InMemoryPermissionRepository permissionRepository;

    public RbacConfig(InMemoryUserRepository userRepository,
                      InMemoryRoleRepository roleRepository,
                      InMemoryPermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    /**
     * 初始化演示数据。
     * <ul>
     *   <li>权限：user:list / user:add / user:delete / order:pay</li>
     *   <li>角色：admin（user:* + order:pay） / user（user:list）</li>
     *   <li>用户：admin/admin123 → admin；alice/alice123 → user</li>
     * </ul>
     */
    @PostConstruct
    public void init() {
        // 权限
        permissionRepository.save(new Permission("user:list", "用户列表", "查询用户列表"));
        permissionRepository.save(new Permission("user:add", "新增用户", "创建新用户"));
        permissionRepository.save(new Permission("user:delete", "删除用户", "删除指定用户"));
        permissionRepository.save(new Permission("order:pay", "订单支付", "支付指定订单"));
        permissionRepository.save(new Permission("order:list", "订单列表", "查询订单列表"));

        // 角色
        Role admin = new Role("admin", "管理员", "拥有所有权限");
        admin.setPermissionCodes(Set.of("user:list", "user:add", "user:delete", "order:pay", "order:list"));
        roleRepository.save(admin);

        Role user = new Role("user", "普通用户", "仅查询用户列表与订单");
        user.setPermissionCodes(Set.of("user:list", "order:list"));
        roleRepository.save(user);

        // 用户
        User adminUser = new User("10001", "admin", "admin123");
        adminUser.setRoleCodes(Set.of("admin"));
        userRepository.save(adminUser);

        User alice = new User("10002", "alice", "alice123");
        alice.setRoleCodes(Set.of("user"));
        userRepository.save(alice);
    }

    /**
     * 注册权限数据源（ddd4j 抽象层）。
     * <p>实际项目中从数据库/缓存查询，此处从 RBAC 仓储聚合查询。
     * <p>注意：数据源必须能根据 AuthPrincipal（subject）查询其角色+权限列表，
     * 用于 ddd4j SubjectKit 的统一鉴权入口。
     */
    @Bean
    public SubjectDataProvider subjectDataProvider() {
        return new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                User user = userRepository.findById(String.valueOf(principal.getLoginId())).orElse(null);
                if (user == null) {
                    return List.of();
                }
                return roleRepository.findAll().stream()
                        .filter(r -> user.getRoleCodes().contains(r.getCode()))
                        .flatMap(r -> r.getPermissionCodes().stream())
                        .distinct()
                        .toList();
            }

            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                User user = userRepository.findById(String.valueOf(principal.getLoginId())).orElse(null);
                if (user == null) {
                    return List.of();
                }
                return List.copyOf(user.getRoleCodes());
            }
        };
    }

}