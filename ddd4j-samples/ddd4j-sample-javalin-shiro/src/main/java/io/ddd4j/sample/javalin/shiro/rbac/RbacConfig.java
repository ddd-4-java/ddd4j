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
package io.ddd4j.sample.javalin.shiro.rbac;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableList;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.javalin.shiro.rbac.domain.Role;
import io.ddd4j.sample.javalin.shiro.rbac.domain.User;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * RBAC 初始化配置：演示账号/角色/权限种子数据 + 注册 SubjectDataProvider。
 *
 * <p>本类与 {@code ddd4j-sample-javalin-satoken} 的 RBAC 业务代码<b>完全一致</b>，
 * 唯一区别是：本类在 {@link #createSubjectDataProvider(InMemoryUserRepository, InMemoryRoleRepository)} 中返回的是基于 {@link RbacService} 派生的
 * SubjectDataProvider（业务实现细节对所有示例相同）；不同框架（Sa-Token/Shiro）的差异在于
 * 启动器调用方式，不在本类中体现。
 *
 * <h2>演示账号</h2>
 * <ul>
 *   <li>admin / admin123：角色=admin，权限=user:*, order:*</li>
 *   <li>zhangsan / pass123：角色=user，权限=user:list, order:create, order:pay</li>
 *   <li>lisi / pass123：角色=user，权限=user:list, order:create</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class RbacConfig {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>RbacConfig</code> \u5b9e\u4f8b\u3002 */
    private RbacConfig() {
    }

    /**
     * 初始化 RBAC 内存数据：种子账号 / 角色 / 权限。
     *
     * <p>业务侧启动时调用本方法，完成种子数据落库。
     *
     * @param rbacService RBAC 服务
     */
    public static void initSeedData(RbacService rbacService) {
        // ==================== 种子角色 ====================
        // admin：拥有全部权限
        rbacService.saveRole(new Role("admin", "管理员",
                new LinkedHashSet<>(ImmutableList.of("user:list", "user:add", "user:update", "user:delete",
                        "order:create", "order:pay", "order:ship", "order:cancel",
                        "permission:list", "role:list"))));
        // user：拥有基本业务权限
        rbacService.saveRole(new Role("user", "普通用户",
                new LinkedHashSet<>(ImmutableList.of("user:list", "order:create", "order:pay"))));

        rbacService.saveRole(new Role("reader", "只读用户",
                new LinkedHashSet<>(ImmutableList.of("order:read"))));

        // ==================== 种子用户 ====================
        // admin：admin 角色
        rbacService.saveUser(new User("admin", "admin123", "管理员",
                ImmutableSet.of("admin"), ImmutableSet.of()));
        // zhangsan：user 角色（业务授权：直接拥有 order:pay）
        rbacService.saveUser(new User("zhangsan", "pass123", "张三",
                ImmutableSet.of("user"), ImmutableSet.of("order:pay")));
        // lisi：user 角色（无额外权限）
        rbacService.saveUser(new User("lisi", "pass123", "李四",
                ImmutableSet.of("user"), ImmutableSet.of()));
        rbacService.saveUser(new User("reader", "sample-password", "订单只读用户",
                ImmutableSet.of("reader"), ImmutableSet.of()));
    }

    /**
     * 注册 SubjectDataProvider：业务侧从 RBAC 仓储派生权限码 / 角色列表。
     *
     * <p>本方法是 RBAC 业务侧与鉴权框架的桥梁：
     * ddd4j-auth-shiro 的 {@code ShiroSubject.isPermitted/hasRole} 会委托此 SPI。
     *
     * @param userRepository 用户仓储
     * @param roleRepository 角色仓储
     * @return 主体数据提供者
     */
    public static SubjectDataProvider createSubjectDataProvider(
            InMemoryUserRepository userRepository,
            InMemoryRoleRepository roleRepository) {
        return new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                if (Objects.isNull(principal)) {
                    return ImmutableList.of();
                }
                Object loginId = principal.getLoginId();
                if (Objects.isNull(loginId)) {
                    return ImmutableList.of();
                }
                return userRepository.findByLoginId(String.valueOf(loginId))
                        .map(rbacServicePerms(roleRepository))
                        .orElse(ImmutableList.of());
            }

            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                if (Objects.isNull(principal)) {
                    return ImmutableList.of();
                }
                Object loginId = principal.getLoginId();
                if (Objects.isNull(loginId)) {
                    return ImmutableList.of();
                }
                return userRepository.findByLoginId(String.valueOf(loginId))
                        .map(User::roles)
                        .map(roles -> (List<String>) ImmutableList.copyOf(roles))
                        .orElse(ImmutableList.of());
            }
        };
    }

    /**
     * \u6d3e\u751f\u7528\u6237\u6700\u7ec8\u6743\u9650\u7801\uff1a\u7528\u6237\u76f4\u63a5\u6743\u9650 \u222a \u89d2\u8272\u6301\u6709\u7684\u6743\u9650\u3002
     *
     * @param roleRepository \u7528\u4e8e\u8bfb\u53d6\u89d2\u8272\u7684\u4ed3\u50a8
     * @return \u5408\u5e76\u7528\u6237\u76f4\u63a5\u6743\u9650\u548c\u89d2\u8272\u6743\u9650\u7684\u51fd\u6570
     */
    private static java.util.function.Function<User, List<String>> rbacServicePerms(InMemoryRoleRepository roleRepository) {
        return user -> {
            Set<String> allPerms = new LinkedHashSet<>(user.permissions());
            for (String roleCode : user.roles()) {
                roleRepository.findByCode(roleCode)
                        .ifPresent(role -> allPerms.addAll(role.permissions()));
            }
            return ImmutableList.copyOf(allPerms);
        };
    }

}
