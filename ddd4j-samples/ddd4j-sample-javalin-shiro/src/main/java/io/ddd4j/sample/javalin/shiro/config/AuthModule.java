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
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.javalin.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.shiro.goods.domain.GoodsRepository;
import io.ddd4j.sample.javalin.shiro.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.shiro.goods.web.GoodsQueryResource;
import io.ddd4j.sample.javalin.shiro.goods.web.GoodsResource;
import io.ddd4j.sample.javalin.shiro.order.application.OrderApplicationService;
import io.ddd4j.sample.javalin.shiro.order.domain.repository.OrderRepository;
import io.ddd4j.sample.javalin.shiro.order.domain.service.OrderDomainService;
import io.ddd4j.sample.javalin.shiro.order.infrastructure.InMemoryOrderRepository;
import io.ddd4j.sample.javalin.shiro.order.web.OrderResource;
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
 * <p>本模块与 {@code ddd4j-sample-javalin-satoken} 的 AuthModule <b>完全一致</b>，
 * 证明切换底层鉴权框架（Sa-Token → Shiro）时 Guice 配置代码零改动。
 *
 * <p>RBAC 仓储实例由外部构造并通过构造函数传入（与 Shiro Realm 共享同一份数据）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AuthModule extends AbstractModule {

    private final InMemoryUserRepository userRepository;
    private final InMemoryRoleRepository roleRepository;
    private final InMemoryPermissionRepository permissionRepository;
    private final RbacService rbacService;

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
                new InMemoryPermissionRepository(),
                null);
    }

    @Override
    protected void configure() {
        // ========================= RBAC 模块 =========================
        RbacService effectiveService = Objects.nonNull(rbacService)
                ? rbacService
                : new RbacService(userRepository, roleRepository, permissionRepository);
        if (Objects.isNull(rbacService)) {
            // 测试 / 独立场景：构造 RbacService 并初始化种子数据
            RbacConfig.initSeedData(effectiveService);
        }

        // 暴露仓储实例（Guice 不需要 bind 静态实例，单例对象直接使用）
        bind(InMemoryUserRepository.class).toInstance(userRepository);
        bind(InMemoryRoleRepository.class).toInstance(roleRepository);
        bind(InMemoryPermissionRepository.class).toInstance(permissionRepository);
        bind(RbacService.class).toInstance(effectiveService);

        // 注册 SubjectDataProvider：从 RBAC 仓储派生权限/角色
        SubjectDataProvider subjectDataProvider = RbacConfig.createSubjectDataProvider(userRepository, roleRepository);
        bind(SubjectDataProvider.class).toInstance(subjectDataProvider);

        // 绑定 RBAC 控制器
        bind(AuthenticationController.class);
        bind(AuthorizationController.class);

        // ========================= Order 模块 =========================
        bind(OrderRepository.class).to(InMemoryOrderRepository.class);
        bind(OrderDomainService.class);
        bind(OrderApplicationService.class);
        bind(OrderResource.class);

        // ========================= Goods 模块 =========================
        bind(GoodsRepository.class).to(InMemoryGoodsRepository.class);
        bind(GoodsApplicationService.class);
        bind(GoodsResource.class);
        bind(GoodsQueryResource.class);

        // 注册 Goods 到 RepositoryRegistry，GoodsQuery 充血查询可定位到具体仓库
        // RepositoryRegistry 的 register 是静态方法，在 Guice 启动后由 JavalinShiroApplication 调用
    }

}