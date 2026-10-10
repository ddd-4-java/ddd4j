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

import io.ddd4j.sample.quarkus.shiro.rbac.InMemoryRoleRepository;
import io.ddd4j.sample.quarkus.shiro.rbac.InMemoryUserRepository;
import io.quarkus.runtime.StartupEvent;
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
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import java.util.HashSet;
import java.util.Set;

/**
 * Quarkus 进程内 Shiro SecurityManager 与 RBAC Realm 装配。
 *
 * <p>用户名和密码已由应用服务校验；Realm 负责建立 Shiro 会话，并从当前 RBAC 仓储实时解析
 * 角色和权限，保证后续 Bearer Session 恢复后的鉴权仍由 Shiro Authorizer 执行。
 */
@ApplicationScoped
public class ShiroRuntimeConfig {

    @Inject
    InMemoryUserRepository userRepository;

    @Inject
    InMemoryRoleRepository roleRepository;

    void onStart(@Observes StartupEvent event) {
        SecurityUtils.setSecurityManager(new DefaultSecurityManager(new RbacRealm(userRepository, roleRepository)));
    }

    /**
     * RBAC 授权域：认证信息由应用服务校验，授权信息从 RBAC 仓储实时解析。
     *
     * <p>登录时建立 Shiro 会话；授权时按用户角色查询角色与权限集合，
     * 供 Shiro Authorizer 执行后续鉴权。
     */
    private static final class RbacRealm extends AuthorizingRealm {

        private final InMemoryUserRepository userRepository;
        private final InMemoryRoleRepository roleRepository;

        private RbacRealm(InMemoryUserRepository userRepository, InMemoryRoleRepository roleRepository) {
            this.userRepository = userRepository;
            this.roleRepository = roleRepository;
            setName("ddd4j-quarkus-rbac");
        }

        /**
         * 执行处理逻辑。
         * @param principals principals 参数。
         * @return 方法执行结果。
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
         * 执行处理逻辑。
         * @param token 访问令牌。
         * @return 方法执行结果。
         * @throws AuthenticationException 出现异常时抛出。
         */
        @Override
        protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
            return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
        }
    }
}
