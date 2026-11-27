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

    /**
     * 读取和保存用户的仓储。
     */
    @Inject
    InMemoryUserRepository userRepository;

    /**
     * 读取和保存角色的仓储。
     */
    @Inject
    InMemoryRoleRepository roleRepository;

    /**
     * 安装当前认证管理器前存在的原实例。
     */
    private SecurityManager previous;
    /**
     * 由当前示例安装的原生安全管理器。
     */
    private DefaultSecurityManager owned;

    /**
     * 读取进程目前登记的 Shiro 安全管理器。
     *
     * @return 当前 Shiro 安全管理器
     */
    private static SecurityManager processManager() {
        // Shiro 的公开 getter 会优先返回线程绑定；这里只读取 setter 修改的静态槽。
        return (SecurityManager) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SecurityUtils.class, "securityManager")));
    }

    /**
     * 在 Quarkus 应用启动时装配并登记 Shiro 安全管理器。
     *
     * @param event 启动、停止或请求处理期间的框架事件
     */
    void onStart(@Observes StartupEvent event) {
        previous = processManager();
        owned = new DefaultSecurityManager(new RbacRealm(userRepository, roleRepository));
        SecurityUtils.setSecurityManager(owned);
    }

    /**
     * 在 Quarkus 应用停止时撤销本示例拥有的 Shiro 注册。
     *
     * @param event 启动、停止或请求处理期间的框架事件
     */
    void onStop(@Observes ShutdownEvent event) {
        destroy();
    }

    /**
     * 清理当前示例拥有的安全管理器并恢复旧管理器。
     */
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

    /**
     * 根据示例账号、角色和权限配置执行 Shiro 认证与授权。
     */
    private static final class RbacRealm extends AuthorizingRealm {

        /**
         * 读取和保存用户的仓储。
         */
        private final InMemoryUserRepository userRepository;
        /**
         * 读取和保存角色的仓储。
         */
        private final InMemoryRoleRepository roleRepository;

        /**
         * 用给定值初始化 <code>RbacRealm</code> 实例。
         *
         * @param userRepository 读取和保存用户的仓储
         * @param roleRepository 读取和保存角色的仓储
         */
        private RbacRealm(InMemoryUserRepository userRepository, InMemoryRoleRepository roleRepository) {
            this.userRepository = userRepository;
            this.roleRepository = roleRepository;
            setName("ddd4j-quarkus-rbac");
        }

        /**
         * 查找当前主体对应的角色和权限并返回给 Shiro。
         *
         * @param principals 当前 Shiro 会话中已认证的身份信息
         * @return 用于角色和权限检查的授权信息
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
         * 根据提交的凭据创建 Shiro 可校验的认证信息。
         *
         * @param token 由 Shiro 传入用于认证的原生登录令牌
         * @return 与示例账号对应的认证信息
         * @throws AuthenticationException 装配示例或处理当前 HTTP 请求失败
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
