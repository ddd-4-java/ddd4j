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
package io.ddd4j.sample.quarkus.shiro.config;

import cn.hutool.core.util.ReflectUtil;
import io.ddd4j.sample.quarkus.shiro.rbac.InMemoryRoleRepository;
import io.ddd4j.sample.quarkus.shiro.rbac.InMemoryUserRepository;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Quarkus 进程内 Shiro SecurityManager 与 RBAC Realm 装配。
 *
 * <p>用户名和密码已由应用服务校验；Realm 负责建立 Shiro 会话，并从当前 RBAC 仓储实时解析
 * 角色和权限，保证后续 Bearer Session 恢复后的鉴权仍由 Shiro Authorizer 执行。
 */
@ApplicationScoped
public class ShiroRuntimeConfig {

    /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u7528\u6237\u7684\u4ed3\u50a8\u3002 */
    @Inject
    InMemoryUserRepository userRepository;

    /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u89d2\u8272\u7684\u4ed3\u50a8\u3002 */
    @Inject
    InMemoryRoleRepository roleRepository;

    /** \u5b89\u88c5\u5f53\u524d\u8ba4\u8bc1\u7ba1\u7406\u5668\u524d\u5b58\u5728\u7684\u539f\u5b9e\u4f8b\u3002 */
    private SecurityManager previous;
    /** \u7531\u5f53\u524d\u793a\u4f8b\u5b89\u88c5\u7684\u539f\u751f\u5b89\u5168\u7ba1\u7406\u5668\u3002 */
    private DefaultSecurityManager owned;

    /**
     * \u8bfb\u53d6\u8fdb\u7a0b\u76ee\u524d\u767b\u8bb0\u7684 Shiro \u5b89\u5168\u7ba1\u7406\u5668\u3002
     * @return \u5f53\u524d Shiro \u5b89\u5168\u7ba1\u7406\u5668
     */
    private static SecurityManager processManager() {
        // Shiro 的公开 getter 会优先返回线程绑定；这里只读取 setter 修改的静态槽。
        return (SecurityManager) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SecurityUtils.class, "securityManager")));
    }

    /**
     * \u5728 Quarkus \u5e94\u7528\u542f\u52a8\u65f6\u88c5\u914d\u5e76\u767b\u8bb0 Shiro \u5b89\u5168\u7ba1\u7406\u5668\u3002
     * @param event \u542f\u52a8\u3001\u505c\u6b62\u6216\u8bf7\u6c42\u5904\u7406\u671f\u95f4\u7684\u6846\u67b6\u4e8b\u4ef6
     */
    void onStart(@Observes StartupEvent event) {
        previous = processManager();
        owned = new DefaultSecurityManager(new RbacRealm(userRepository, roleRepository));
        SecurityUtils.setSecurityManager(owned);
    }

    /**
     * \u5728 Quarkus \u5e94\u7528\u505c\u6b62\u65f6\u64a4\u9500\u672c\u793a\u4f8b\u62e5\u6709\u7684 Shiro \u6ce8\u518c\u3002
     * @param event \u542f\u52a8\u3001\u505c\u6b62\u6216\u8bf7\u6c42\u5904\u7406\u671f\u95f4\u7684\u6846\u67b6\u4e8b\u4ef6
     */
    void onStop(@Observes ShutdownEvent event) {
        destroy();
    }

    /** \u6e05\u7406\u5f53\u524d\u793a\u4f8b\u62e5\u6709\u7684\u5b89\u5168\u7ba1\u7406\u5668\u5e76\u6062\u590d\u65e7\u7ba1\u7406\u5668\u3002 */
    @PreDestroy
    void destroy() {
        DefaultSecurityManager current = owned;
        owned = null;
        if (Objects.isNull(current)) {
            return;
        }
        try {
            if (processManager() == current) {
                SecurityUtils.setSecurityManager(previous);
            }
        } finally {
            current.destroy();
        }
    }

    /** \u6839\u636e\u793a\u4f8b\u8d26\u53f7\u3001\u89d2\u8272\u548c\u6743\u9650\u914d\u7f6e\u6267\u884c Shiro \u8ba4\u8bc1\u4e0e\u6388\u6743\u3002 */
    private static final class RbacRealm extends AuthorizingRealm {

        /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u7528\u6237\u7684\u4ed3\u50a8\u3002 */
        private final InMemoryUserRepository userRepository;
        /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u89d2\u8272\u7684\u4ed3\u50a8\u3002 */
        private final InMemoryRoleRepository roleRepository;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>RbacRealm</code> \u5b9e\u4f8b\u3002
         * @param userRepository \u8bfb\u53d6\u548c\u4fdd\u5b58\u7528\u6237\u7684\u4ed3\u50a8
         * @param roleRepository \u8bfb\u53d6\u548c\u4fdd\u5b58\u89d2\u8272\u7684\u4ed3\u50a8
         */
        private RbacRealm(InMemoryUserRepository userRepository, InMemoryRoleRepository roleRepository) {
            this.userRepository = userRepository;
            this.roleRepository = roleRepository;
            setName("ddd4j-quarkus-rbac");
        }

        /**
         * \u67e5\u627e\u5f53\u524d\u4e3b\u4f53\u5bf9\u5e94\u7684\u89d2\u8272\u548c\u6743\u9650\u5e76\u8fd4\u56de\u7ed9 Shiro\u3002
         * @param principals \u5f53\u524d Shiro \u4f1a\u8bdd\u4e2d\u5df2\u8ba4\u8bc1\u7684\u8eab\u4efd\u4fe1\u606f
         * @return \u7528\u4e8e\u89d2\u8272\u548c\u6743\u9650\u68c0\u67e5\u7684\u6388\u6743\u4fe1\u606f
         */
        @Override
        protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
            String userId = String.valueOf(principals.getPrimaryPrincipal());
            SimpleAuthorizationInfo authorization = new SimpleAuthorizationInfo();
            userRepository.findById(userId).ifPresent(user -> {
                Set<String> roles = new HashSet<>(user.getRoleCodes());
                Set<String> permissions = new HashSet<>();
                roles.forEach(roleCode -> roleRepository.findByCode(roleCode)
                        .ifPresent(role -> permissions.addAll(role.getPermissionCodes())));
                authorization.setRoles(roles);
                authorization.setStringPermissions(permissions);
            });
            return authorization;
        }

        /**
         * \u6839\u636e\u63d0\u4ea4\u7684\u51ed\u636e\u521b\u5efa Shiro \u53ef\u6821\u9a8c\u7684\u8ba4\u8bc1\u4fe1\u606f\u3002
         * @param token \u7531 Shiro \u4f20\u5165\u7528\u4e8e\u8ba4\u8bc1\u7684\u539f\u751f\u767b\u5f55\u4ee4\u724c
         * @return \u4e0e\u793a\u4f8b\u8d26\u53f7\u5bf9\u5e94\u7684\u8ba4\u8bc1\u4fe1\u606f
         * @throws AuthenticationException \u88c5\u914d\u793a\u4f8b\u6216\u5904\u7406\u5f53\u524d HTTP \u8bf7\u6c42\u5931\u8d25
         */
        @Override
        protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
            var user = userRepository.findById(String.valueOf(token.getPrincipal()))
                    .orElseThrow(() -> new org.apache.shiro.authc.UnknownAccountException("unknown user"));
            if (user.isDisabled()) {
                throw new org.apache.shiro.authc.DisabledAccountException("disabled user");
            }
            return new SimpleAuthenticationInfo(user.getId(), user.getPassword(), getName());
        }
    }
}
