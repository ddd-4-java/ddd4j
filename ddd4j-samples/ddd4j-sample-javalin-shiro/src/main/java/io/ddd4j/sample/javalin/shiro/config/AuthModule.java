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

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.javalin.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.shiro.goods.domain.GoodsRepository;
import io.ddd4j.sample.javalin.shiro.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.shiro.goods.web.GoodsQueryResource;
import io.ddd4j.sample.javalin.shiro.goods.web.GoodsResource;
import io.ddd4j.sample.javalin.shiro.rbac.RbacConfig;
import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthenticationController;
import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthorizationController;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;

import java.util.Objects;

/**
 * 鉴权 + 业务 Guice 模块：注册 RBAC 数据源、SubjectDataProvider 与所有业务服务/资源绑定。
 *
 * <p>原生应用传入与 Shiro Realm 共享的仓储和 RBAC 服务。独立使用时，本模块创建内存仓储与演示数据。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AuthModule extends AbstractModule {

    /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u7528\u6237\u7684\u4ed3\u50a8\u3002 */
    private final InMemoryUserRepository userRepository;
    /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u89d2\u8272\u7684\u4ed3\u50a8\u3002 */
    private final InMemoryRoleRepository roleRepository;
    /** \u67e5\u8be2\u548c\u7ef4\u62a4\u6743\u9650\u5b9a\u4e49\u7684\u4ed3\u50a8\u3002 */
    private final InMemoryPermissionRepository permissionRepository;
    /** \u6267\u884c\u57fa\u4e8e\u89d2\u8272\u7684\u6388\u6743\u4e0e\u5206\u914d\u64cd\u4f5c\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    private final RbacService rbacService;

    /**
     * 创建认证模块。
     *
     * @param userRepository       用户仓储
     * @param roleRepository       角色仓储
     * @param permissionRepository 权限仓储
     * @param rbacService          RBAC 服务
     */
    public AuthModule(InMemoryUserRepository userRepository,
                      InMemoryRoleRepository roleRepository,
                      InMemoryPermissionRepository permissionRepository,
                      RbacService rbacService) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.permissionRepository = Objects.requireNonNull(permissionRepository, "permissionRepository must not be null");
        this.rbacService = Objects.requireNonNull(rbacService, "rbacService must not be null");
    }

    /**
     * 无参构造（仅用于测试或独立场景）。
     *
     * <p>会构造全新的内存仓储与种子数据。
     */
    public AuthModule() {
        this(new InMemoryUserRepository(),
                new InMemoryRoleRepository(),
                new InMemoryPermissionRepository());
    }

    /**
     * \u4fdd\u5b58\u6784\u5efa\u8ba4\u8bc1\u4e0e\u6388\u6743\u7ec4\u4ef6\u9700\u8981\u7684\u793a\u4f8b\u4ed3\u50a8\u3002
     * @param userRepository \u5f53\u524d\u793a\u4f8b\u4f7f\u7528\u7684\u5185\u5b58\u7528\u6237\u4ed3\u50a8
     * @param roleRepository \u5f53\u524d\u793a\u4f8b\u4f7f\u7528\u7684\u5185\u5b58\u89d2\u8272\u4ed3\u50a8
     * @param permissionRepository \u5f53\u524d\u793a\u4f8b\u4f7f\u7528\u7684\u5185\u5b58\u6743\u9650\u4ed3\u50a8
     */
    private AuthModule(InMemoryUserRepository userRepository,
                       InMemoryRoleRepository roleRepository,
                       InMemoryPermissionRepository permissionRepository) {
        this(userRepository, roleRepository, permissionRepository,
                new RbacService(userRepository, roleRepository, permissionRepository));
        RbacConfig.initSeedData(rbacService);
    }

    /** \u4e3a\u5f53\u524d\u8ba4\u8bc1\u793a\u4f8b\u5b89\u88c5\u7528\u6237\u3001\u89d2\u8272\u3001\u6743\u9650\u53ca\u4e3b\u4f53\u76f8\u5173 Guice \u7ed1\u5b9a\u3002 */
    @Override
    protected void configure() {
        // ========================= RBAC 模块 =========================
        // 暴露仓储实例（Guice 不需要 bind 静态实例，单例对象直接使用）
        bind(InMemoryUserRepository.class).toInstance(userRepository);
        bind(InMemoryRoleRepository.class).toInstance(roleRepository);
        bind(InMemoryPermissionRepository.class).toInstance(permissionRepository);
        bind(RbacService.class).toInstance(rbacService);

        // 注册 SubjectDataProvider：从 RBAC 仓储派生权限/角色
        SubjectDataProvider subjectDataProvider = RbacConfig.createSubjectDataProvider(userRepository, roleRepository);
        bind(SubjectDataProvider.class).toInstance(subjectDataProvider);

        // 绑定 RBAC 控制器
        bind(AuthenticationController.class);
        bind(AuthorizationController.class);


        // ========================= Goods 模块 =========================
        bind(GoodsRepository.class).to(InMemoryGoodsRepository.class).in(Singleton.class);
        bind(GoodsApplicationService.class);
        bind(GoodsResource.class);
        bind(GoodsQueryResource.class);

        // 注册 Goods 到 RepositoryRegistry，GoodsQuery 充血查询可定位到具体仓库
        // RepositoryRegistry 的 register 是静态方法，在 Guice 启动后由 JavalinShiroApplication 调用
    }

}
