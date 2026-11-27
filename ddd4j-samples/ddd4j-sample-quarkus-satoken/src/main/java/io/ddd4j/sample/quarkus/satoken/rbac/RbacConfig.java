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
package io.ddd4j.sample.quarkus.satoken.rbac;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.CollKit;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

/**
 * RBAC 配置：初始化内存数据 + 注册 SubjectDataProvider。
 *
 * <p>演示启动期种子数据：
 * <ul>
 *   <li>权限：user:list / user:add / user:delete / order:pay / order:cancel / admin:*</li>
 *   <li>角色：admin（全部权限）/ user（user:list）</li>
 *   <li>用户：admin/123456、user/123456、disabled/123456（disabled=true）</li>
 * </ul>
 *
 * <p>{@link SubjectDataProvider} 从 RBAC 仓储实时聚合用户角色/权限，用于 Sa-Token 注解校验。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class RbacConfig {

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
     * 查询和维护权限定义的仓储。
     */
    @Inject
    InMemoryPermissionRepository permissionRepository;

    /**
     * 应用启动时初始化演示数据。
     */
    private SubjectDataProvider previousData;
    /**
     * 为认证主体提供角色、权限等业务数据的组件。
     */
    private SubjectDataProvider ownedData;

    /**
     * 在示例应用启动后装配 RBAC 角色和权限组件。
     *
     * @param event 启动、停止或请求处理期间的框架事件
     */
    void onStart(@Observes StartupEvent event) {
        previousData = SubjectKit.dataProvider;
        reset();
    }

    /**
     * 重建样例的确定性 RBAC 金标数据。
     */
    public synchronized void reset() {
        userRepository.clear();
        roleRepository.clear();
        permissionRepository.clear();

        // 1. 权限
        permissionRepository.save(new Permission("user:list", "用户列表", "查询用户列表"));
        permissionRepository.save(new Permission("user:add", "新增用户", "新增用户"));
        permissionRepository.save(new Permission("user:delete", "删除用户", "删除用户"));
        permissionRepository.save(new Permission("order:pay", "支付订单", "支付订单"));
        permissionRepository.save(new Permission("order:cancel", "取消订单", "取消订单"));
        permissionRepository.save(new Permission("admin:dashboard", "管理面板", "访问管理面板"));

        // 2. 角色（绑定权限）
        Role admin = new Role("admin", "管理员", "拥有全部权限", new HashSet<>(java.util.Arrays.asList(
                "user:list", "user:add", "user:delete",
                "order:pay", "order:cancel", "admin:dashboard")));
        roleRepository.save(admin);

        Role user = new Role("user", "普通用户", "基础用户", new HashSet<>(java.util.Arrays.asList("user:list")));
        roleRepository.save(user);

        // 3. 用户（绑定角色）
        userRepository.save(new User("u-admin", "admin", "管理员", "123456",
                new HashSet<>(java.util.Arrays.asList("admin", "user")), false));
        userRepository.save(new User("u-user", "user", "普通用户", "123456",
                new HashSet<>(java.util.Arrays.asList("user")), false));
        userRepository.save(new User("u-disabled", "disabled", "已禁用用户", "123456",
                new HashSet<>(java.util.Arrays.asList("user")), true));

        ownedData = subjectDataProvider();
        SubjectKit.setDataProvider(ownedData);
    }

    /**
     * 在示例应用停止时释放当前 RBAC 装配拥有的组件。
     *
     * @param event 启动、停止或请求处理期间的框架事件
     */
    void onStop(@Observes io.quarkus.runtime.ShutdownEvent event) {
        if (SubjectKit.dataProvider == ownedData) {
            SubjectKit.setDataProvider(previousData);
        }
    }

    /**
     * RBAC 权限数据源：从 User 到 Role 再到 Permission 实时聚合。
     *
     * @return 主体数据提供者
     */
    @Produces
    @Singleton
    public SubjectDataProvider subjectDataProvider() {
        return new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                if (Objects.isNull(principal) || Objects.isNull(principal.getUserId())) {
                    return java.util.Collections.emptyList();
                }
                String userId = String.valueOf(principal.getUserId());
                return userRepository.findById(userId)
                        .map(user -> aggregatePermissions(user.getRoleCodes()))
                        .orElse(java.util.Collections.emptyList());
            }

            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                if (Objects.isNull(principal) || Objects.isNull(principal.getUserId())) {
                    return java.util.Collections.emptyList();
                }
                String userId = String.valueOf(principal.getUserId());
                return userRepository.findById(userId)
                        .map(user -> java.util.Collections.unmodifiableList(new java.util.ArrayList<>(user.getRoleCodes())))
                        .orElse(java.util.Collections.emptyList());
            }
        };
    }

    /**
     * 聚合多角色下的全部权限编码（去重）。
     *
     * @param roleCodes 待查找或校验的角色编码
     * @return 所有匹配角色拥有的权限集合
     */
    private List<String> aggregatePermissions(Set<String> roleCodes) {
        if (CollKit.isEmpty(roleCodes)) {
            return java.util.Collections.emptyList();
        }
        Set<String> aggregated = new HashSet<>();
        for (String roleCode : roleCodes) {
            roleRepository.findByCode(roleCode)
                    .ifPresent(role -> {
                        if (Objects.nonNull(role.getPermissionCodes())) {
                            aggregated.addAll(role.getPermissionCodes());
                        }
                    });
        }
        return new ArrayList<>(aggregated);
    }

}
