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
package io.ddd4j.sample.javalin.satoken.config;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.sample.javalin.satoken.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.satoken.goods.domain.GoodsRepository;
import io.ddd4j.sample.javalin.satoken.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.satoken.goods.web.GoodsQueryResource;
import io.ddd4j.sample.javalin.satoken.goods.web.GoodsResource;
import io.ddd4j.sample.javalin.satoken.rbac.RbacConfig;
import io.ddd4j.sample.javalin.satoken.rbac.application.RbacService;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.UserRepository;
import io.ddd4j.sample.javalin.satoken.rbac.infrastructure.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.satoken.rbac.infrastructure.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.satoken.rbac.infrastructure.InMemoryUserRepository;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthenticationController;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthorizationController;

/**
 * RBAC + 业务 Guice 模块：注册权限数据源与所有业务服务/资源绑定。
 *
 * <p>Javalin 没有 DI 容器，使用 Google Guice 代替 Spring @Configuration / CDI @ApplicationScoped。
 * RBAC 业务代码（User/Role/Permission/Repository/Service）与 Spring 示例完全一致，
 * 仅路由层（Controller）采用 Javalin {@link io.javalin.apibuilder.ApiBuilder} 风格。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AuthModule extends AbstractModule {

    /** \u4e3a\u5f53\u524d\u8ba4\u8bc1\u793a\u4f8b\u5b89\u88c5\u7528\u6237\u3001\u89d2\u8272\u3001\u6743\u9650\u53ca\u4e3b\u4f53\u76f8\u5173 Guice \u7ed1\u5b9a\u3002 */
    @Override
    protected void configure() {
        // ========================= RBAC 模块 =========================
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryRoleRepository roleRepository = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissionRepository = new InMemoryPermissionRepository();

        // 暴露仓储接口到具体实现
        bind(UserRepository.class).toInstance(userRepository);
        bind(RoleRepository.class).toInstance(roleRepository);
        bind(PermissionRepository.class).toInstance(permissionRepository);

        RbacService rbacService = new RbacService(userRepository, roleRepository, permissionRepository);
        bind(RbacService.class).toInstance(rbacService);

        // 注册 SubjectDataProvider：RbacService 本身实现了 SubjectDataProvider
        SubjectDataProvider subjectDataProvider = RbacConfig.subjectDataProvider(rbacService);
        bind(SubjectDataProvider.class).toInstance(subjectDataProvider);

        // 绑定 RBAC 控制器
        bind(AuthenticationController.class);
        bind(AuthorizationController.class);


        // ========================= Goods 模块 =========================
        bind(GoodsRepository.class).to(InMemoryGoodsRepository.class).in(Singleton.class);
        bind(GoodsApplicationService.class);
        bind(GoodsResource.class);
        bind(GoodsQueryResource.class);

        // RepositoryRegistry 的 register 是静态方法，在 Guice 启动后由 JavalinSaTokenApplication 调用
    }

}
