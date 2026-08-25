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
package io.ddd4j.sample.javalin.satoken;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.subject.SubjectStrategy;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.guice.subject.GuiceSubjectProvider;
import io.ddd4j.sample.javalin.satoken.config.AuthModule;
import io.ddd4j.sample.javalin.satoken.goods.domain.Goods;
import io.ddd4j.sample.javalin.satoken.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.satoken.goods.web.GoodsQueryResource;
import io.ddd4j.sample.javalin.satoken.goods.web.GoodsResource;
import io.ddd4j.sample.javalin.satoken.order.web.OrderResource;
import io.ddd4j.sample.javalin.satoken.rbac.RbacConfig;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.UserRepository;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthenticationController;
import io.ddd4j.sample.javalin.satoken.rbac.web.AuthorizationController;
import io.javalin.Javalin;
import io.javalin.apibuilder.ApiBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * Javalin + Guice + Sa-Token + RBAC 鉴权 + DDD 业务示例启动类。
 *
 * <h3>核心要点</h3>
 * <p>Javalin 本身<b>没有 DI 容器</b>，使用 Google Guice 注入业务服务。
 * 业务代码（RBAC User/Role/Permission/Repository/Service）与 Spring 示例完全一致，
 * 仅 Controller 层使用 Javalin {@link ApiBuilder} 风格。
 *
 * <h3>RBAC 演示要点</h3>
 * <ul>
 *   <li>完整 User / Role / Permission 内存 CRUD（{@link AuthorizationController}）</li>
 *   <li>登录 / 登出 / 当前用户 / 权限角色校验（{@link AuthenticationController}）</li>
 *   <li>业务接口鉴权示范：{@code GET /auth/users} 需要 user:list 权限；{@code POST /auth/orders/{id}/pay} 需要 order:pay 权限；{@code DELETE /auth/users/{id}} 需要 admin 角色 + user:delete 权限（组合鉴权）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class JavalinSaTokenApplication {

    /**
     * Javalin 监听端口
     */
    public static final int PORT = 8085;

    public static void main(String[] args) {
        // ==================== 1. 创建 Guice 注入器 ====================
        Module authModule = new AuthModule();
        Injector injector = Guice.createInjector(authModule);

        // ==================== 2. 预置 RBAC 演示数据 ====================
        RbacConfig.initRbacData(
                injector.getInstance(UserRepository.class),
                injector.getInstance(RoleRepository.class),
                injector.getInstance(PermissionRepository.class));

        // ==================== 3. 注册 SubjectKit 所需 SPI ====================
        SubjectProvider subjectProvider = new GuiceSubjectProvider();
        SubjectKit.register(subjectProvider);

        SubjectDataProvider subjectDataProvider = injector.getInstance(SubjectDataProvider.class);
        SubjectKit.setDataProvider(subjectDataProvider);

        SubjectKit.setStrategy(SubjectStrategy.instance);

        // ==================== 4. 注册 Goods 仓储到 RepositoryRegistry（充血查询需要） ====================
        RepositoryRegistry.register(Goods.class, new InMemoryGoodsRepository());

        // ==================== 5. 获取 RBAC 控制器与 Resource 实例 ====================
        AuthenticationController authController = injector.getInstance(AuthenticationController.class);
        AuthorizationController authzController = injector.getInstance(AuthorizationController.class);
        OrderResource orderResource = injector.getInstance(OrderResource.class);
        GoodsResource goodsResource = injector.getInstance(GoodsResource.class);
        GoodsQueryResource goodsQueryResource = injector.getInstance(GoodsQueryResource.class);

        // ==================== 6. 启动 Javalin 并注册路由 ====================
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.routes.apiBuilder(() -> {
                // ========== Authentication 路由（/auth/*） ==========
                authController.routes().addEndpoints();

                // ========== Authorization 路由（/rbac/admin/*，RBAC 管理） ==========
                ApiBuilder.path("rbac", authzController.routes());

                // ========== Order 路由（EndpointGroup） ==========
                orderResource.routes().addEndpoints();

                // ========== Goods 路由（写侧 + 读侧） ==========
                goodsQueryResource.routes().addEndpoints();
                goodsResource.routes().addEndpoints();
            });
        });

        app.start(PORT);
        log.info("Javalin + Sa-Token + RBAC + DDD sample started at http://localhost:{}", PORT);
        log.info("Authentication endpoints: /auth/login, /auth/me, /auth/admin, /auth/users");
        log.info("Authorization endpoints: /rbac/admin/users, /rbac/admin/roles, /rbac/admin/permissions");
        log.info("Order endpoints: /orders, /orders/{id}, /orders/{id}/pay");
        log.info("Goods endpoints: /api/goods, /api/goods/page, /api/goods/list");
        log.info("Authorization examples: GET /auth/users, POST /auth/orders/{id}/pay, DELETE /auth/users/{id}");
    }

}
