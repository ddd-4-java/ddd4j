/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.javalin.shiro.config;

import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.apache.shiro.authc.credential.SimpleCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 鉴权配置：引导 Shiro SecurityManager + 基于 RBAC 的自定义 Realm。
 *
 * <p>Javalin 没有 DI 容器，Shiro 适配层不会自动注入 SecurityManager，
 * 业务方需在启动期手动通过 {@link SecurityUtils#setSecurityManager(SecurityManager)}
 * 设置全局 SecurityManager。
 *
 * <p>本类演示<b>基于 RBAC 的 Shiro 集成</b>：自定义 {@link RbacRealm} 从 RBAC 仓储
 * 读取账号/角色/权限信息并响应 Shiro 的认证/授权查询。角色与权限的派生计算通过
 * {@link RbacService#computeEffectivePermissions} 完成（用户直接权限 ∪ 角色持有权限）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class AuthConfig {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>AuthConfig</code> \u5b9e\u4f8b\u3002 */
    private AuthConfig() {
    }

    /**
     * 初始化 Shiro SecurityManager 并注册到全局。
     *
     * <p>使用 {@link RbacRealm} 完成账号/角色/权限的鉴权与授权委托，
     * 业务侧只关心 RBAC 仓储，Shiro 的 Realm 只是适配层。
     *
     * @param userRepository RBAC 用户仓储
     * @param roleRepository RBAC 角色仓储
     * @param rbacService    RBAC 业务服务（用于派生有效权限）
     */
    public static void initShiro(InMemoryUserRepository userRepository,
                                 InMemoryRoleRepository roleRepository,
                                 RbacService rbacService) {
        // 1. 构造基于 RBAC 的 Realm
        Realm realm = new RbacRealm(userRepository, roleRepository, rbacService);

        // 2. 构造 SecurityManager（使用 Shiro 默认实现）
        SecurityManager securityManager = new org.apache.shiro.mgt.DefaultSecurityManager(realm);

        // 3. 注册到全局
        SecurityUtils.setSecurityManager(securityManager);

        log.info("Shiro SecurityManager initialized with RBAC realm: {}", realm.getName());
    }

    /**
     * 创建简单凭据匹配器（明文密码比对，仅供演示）。
     *
     * @return 凭据匹配器
     */
    public static CredentialsMatcher createSimpleCredentialsMatcher() {
        return new SimpleCredentialsMatcher();
    }

    /**
     * 获取当前线程的 Shiro Subject（辅助方法）。
     *
     * @return 当前主体
     */
    public static Subject currentSubject() {
        return SecurityUtils.getSubject();
    }

    // ====================================================================
    // 内部：基于 RBAC 的自定义 Shiro Realm
    // ====================================================================

    /**
     * 基于 RBAC 仓储的 Shiro Realm。
     *
     * <p>认证：从 {@link InMemoryUserRepository} 查询账号并比对密码。
     * 授权：从 RBAC 仓储派生角色与权限集合（{@link RbacService#computeEffectivePermissions}）。
     *
     * <p>本类仅在 Shiro Realm 层做账号/权限映射，业务代码完全不感知 Shiro。
     */
    public static class RbacRealm extends AuthorizingRealm {

        /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u7528\u6237\u7684\u4ed3\u50a8\u3002 */
        private final InMemoryUserRepository userRepository;
        /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u89d2\u8272\u7684\u4ed3\u50a8\u3002 */
        private final InMemoryRoleRepository roleRepository;
        /** \u6267\u884c\u57fa\u4e8e\u89d2\u8272\u7684\u6388\u6743\u4e0e\u5206\u914d\u64cd\u4f5c\u7684\u5e94\u7528\u670d\u52a1\u3002 */
        private final RbacService rbacService;

        /**
         * 创建 RBAC Realm。
         *
         * @param userRepository 用户仓储
         * @param roleRepository 角色仓储
         * @param rbacService    RBAC 服务
         */
        public RbacRealm(InMemoryUserRepository userRepository,
                         InMemoryRoleRepository roleRepository,
                         RbacService rbacService) {
            this.userRepository = userRepository;
            this.roleRepository = roleRepository;
            this.rbacService = rbacService;
            setName("rbacRealm");
            setCredentialsMatcher(new SimpleCredentialsMatcher());
        }

        /**
         * \u6839\u636e\u63d0\u4ea4\u7684\u51ed\u636e\u521b\u5efa Shiro \u53ef\u6821\u9a8c\u7684\u8ba4\u8bc1\u4fe1\u606f\u3002
         * @param token \u7531 Shiro \u4f20\u5165\u7528\u4e8e\u8ba4\u8bc1\u7684\u539f\u751f\u767b\u5f55\u4ee4\u724c
         * @return \u4e0e\u793a\u4f8b\u8d26\u53f7\u5bf9\u5e94\u7684\u8ba4\u8bc1\u4fe1\u606f
         * @throws AuthenticationException \u88c5\u914d\u793a\u4f8b\u6216\u5904\u7406\u5f53\u524d HTTP \u8bf7\u6c42\u5931\u8d25
         */
        @Override
        protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
            UsernamePasswordToken upt = (UsernamePasswordToken) token;
            String loginId = upt.getUsername();
            String password = new String(upt.getPassword());

            io.ddd4j.sample.javalin.shiro.rbac.domain.User user = userRepository.findByLoginId(loginId)
                    .orElseThrow(() -> new UnknownAccountException("user not found: " + loginId));
            if (!user.password().equals(password)) {
                throw new AuthenticationException("invalid credentials");
            }
            // Shiro Principal 使用 RBAC User 实例（业务侧通过 SubjectKit.getPrincipal() 取回）
            return new SimpleAuthenticationInfo(user, user.password().toCharArray(), getName());
        }

        /**
         * \u67e5\u627e\u5f53\u524d\u4e3b\u4f53\u5bf9\u5e94\u7684\u89d2\u8272\u548c\u6743\u9650\u5e76\u8fd4\u56de\u7ed9 Shiro\u3002
         * @param principals \u5f53\u524d Shiro \u4f1a\u8bdd\u4e2d\u5df2\u8ba4\u8bc1\u7684\u8eab\u4efd\u4fe1\u606f
         * @return \u7528\u4e8e\u89d2\u8272\u548c\u6743\u9650\u68c0\u67e5\u7684\u6388\u6743\u4fe1\u606f
         */
        @Override
        protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
            Object primary = principals.getPrimaryPrincipal();
            if (!(primary instanceof io.ddd4j.sample.javalin.shiro.rbac.domain.User)) {
                return new SimpleAuthorizationInfo();
            }
            io.ddd4j.sample.javalin.shiro.rbac.domain.User user =
                    (io.ddd4j.sample.javalin.shiro.rbac.domain.User) primary;

            SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
            // 角色：直接来自 RBAC User.roles()
            info.addRoles(user.roles());
            // 权限：派生计算（用户直接权限 ∪ 角色持有权限）
            Set<String> perms = rbacService.computeEffectivePermissions(user);
            info.addStringPermissions(new LinkedHashSet<>(perms));
            return info;
        }
    }

}
