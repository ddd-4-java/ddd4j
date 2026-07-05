package io.ddd4j.sample.spring.shiro.config;

import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.Role;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.User;
import io.ddd4j.sample.spring.shiro.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.spring.shiro.rbac.domain.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Shiro 鉴权配置：注册权限数据源 + 配置 Shiro Realm + 启用注解拦截。
 *
 * <p>本配置是 ddd4j-sample-spring-shiro 的核心：
 * <ul>
 *   <li>将 {@link RbacConfig#subjectDataProvider()} 写回 {@link SubjectKit}，
 *       使 {@code SubjectKit.hasRole/hasPermission} 能委托给 RBAC 数据源</li>
 *   <li>配置 Shiro {@link AuthorizingRealm}：doGetAuthenticationInfo 从 RBAC 用户仓储校验密码，
 *       doGetAuthorizationInfo 从 RBAC 角色仓储聚合权限</li>
 *   <li>启用 {@link AuthorizationAttributeSourceAdvisor}：让 {@code @RequiresAuthentication} /
 *       {@code @RequiresRoles} / {@code @RequiresPermissions} 注解生效</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
public class AuthConfig {

    private final SubjectDataProvider subjectDataProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Autowired
    public AuthConfig(SubjectDataProvider subjectDataProvider,
                      UserRepository userRepository,
                      RoleRepository roleRepository) {
        this.subjectDataProvider = subjectDataProvider;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * 启动时把权限数据源写回 SubjectKit，使 SubjectKit.hasRole/hasPermission 可用。
     */
    @PostConstruct
    public void registerSubjectKitDataProvider() {
        SubjectKit.setDataProvider(subjectDataProvider);
    }

    // ============================ Shiro Realm（RBAC 数据源）============================

    /**
     * RBAC 数据源 Realm：从 {@link UserRepository} / {@link RoleRepository} 读取用户、角色、权限。
     *
     * <p>登录（doGetAuthenticationInfo）：校验用户名是否存在、密码是否匹配、状态是否启用。
     * <br>授权（doGetAuthorizationInfo）：聚合用户的所有角色编码和权限编码。
     */
    @Bean
    public AuthorizingRealm rbacRealm() {
        return new AuthorizingRealm() {

            @Override
            protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
                    throws AuthenticationException {
                UsernamePasswordToken upt = (UsernamePasswordToken) token;
                String userId = upt.getUsername();
                User user = userRepository.findById(userId).orElse(null);
                if (Objects.isNull(user)) {
                    throw new AuthenticationException("user not found: " + userId);
                }
                if (user.getStatus() != User.Status.ENABLED) {
                    throw new AuthenticationException("user disabled: " + userId);
                }
                return new SimpleAuthenticationInfo(user.getUserId(), user.getPassword(), getName());
            }

            @Override
            protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
                String userId = (String) principals.getPrimaryPrincipal();
                SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
                Set<String> roles = new LinkedHashSet<>();
                Set<String> permissions = new LinkedHashSet<>();
                User user = userRepository.findById(userId).orElse(null);
                if (Objects.nonNull(user)) {
                    for (String roleId : user.getRoleIds()) {
                        Role role = roleRepository.findById(roleId).orElse(null);
                        if (role == null) {
                            continue;
                        }
                        roles.add(role.getRoleCode());
                        for (String permissionId : role.getPermissionIds()) {
                            // 权限 ID 即权限 Code（简化示例）
                            permissions.add(permissionId);
                        }
                    }
                }
                info.setRoles(roles);
                info.setStringPermissions(permissions);
                return info;
            }
        };
    }

    // ============================ Shiro SecurityManager ============================

    /**
     * Shiro SecurityManager：注入 RBAC Realm。
     */
    @Bean
    @DependsOn("rbacRealm")
    public SecurityManager securityManager(AuthorizingRealm rbacRealm) {
        DefaultSecurityManager securityManager = new DefaultSecurityManager();
        securityManager.setRealm(rbacRealm);
        SecurityUtils.setSecurityManager(securityManager);
        return securityManager;
    }

    // ============================ 注解拦截（AOP 切面）============================

    /**
     * 启用 Shiro 注解（{@code @RequiresAuthentication} / {@code @RequiresRoles} / {@code @RequiresPermissions}）。
     */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(SecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager);
        return advisor;
    }

    /**
     * 启用 Spring AOP 自动代理（让 Advisor 能拦截注解方法）。
     */
    @Bean
    @DependsOn("securityManager")
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
        return creator;
    }

}