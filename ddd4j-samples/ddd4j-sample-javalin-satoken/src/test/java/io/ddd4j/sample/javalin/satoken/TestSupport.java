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

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaRequestForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectStrategy;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.javalin.satoken.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.satoken.goods.domain.Goods;
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
import io.javalin.Javalin;
import io.javalin.apibuilder.ApiBuilder;
import io.javalin.json.JavalinJackson;

/**
 * 测试基础设施：手动创建 Guice 注入器并启动 Javalin。
 *
 * <p>测试侧直接构造资源和服务，使 HTTP 集成测试聚焦于路由、认证和领域行为，
 * 避免把 Guice 容器生命周期混入每个测试用例。
 */
public final class TestSupport {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>TestSupport</code> \u5b9e\u4f8b\u3002 */
    private TestSupport() {
    }

    // ============================ 测试用 SPI 实现 ============================

    /**
     * 启动 Javalin 应用：注册 RBAC 种子数据 + Goods 仓储 + SubjectKit SPI + 路由。
     *
     * @return 已启动的 Javalin 实例（绑定端口随机）
     */
    public static Javalin start() {
        // 0) 注入测试需要的核心 SPI（领域事件发布、i18n）
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, new NoopDomainEventPublisher());
        BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, new NoopI18nProvider());

        // 1) 手动构造所有组件（不依赖 Guice 自动注入）
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryRoleRepository roleRepository = new InMemoryRoleRepository();
        InMemoryPermissionRepository permissionRepository = new InMemoryPermissionRepository();
        RbacService rbacService = new RbacService(userRepository, roleRepository, permissionRepository);

        RbacConfig.initRbacData(userRepository, roleRepository, permissionRepository);

        // 2) SubjectKit SPI（用 TestSubjectProvider，getUserId 从 principal 读取，避开 sa-token-jwt 依赖）
        SubjectKit.register(new TestSubjectProvider());
        SubjectKit.setDataProvider(rbacService);
        SubjectKit.setStrategy(SubjectStrategy.instance);

        // 3) Goods 仓储与业务服务
        InMemoryGoodsRepository goodsRepository = new InMemoryGoodsRepository();
        RepositoryRegistry.register(Goods.class, goodsRepository);
        GoodsApplicationService goodsApplicationService = new GoodsApplicationService(goodsRepository);
        GoodsResource goodsResource = new GoodsResource(goodsApplicationService);
        GoodsQueryResource goodsQueryResource = new GoodsQueryResource(goodsApplicationService);


        // 5) RBAC 控制器
        AuthenticationController authController = new AuthenticationController(rbacService);
        AuthorizationController authzController = new AuthorizationController(rbacService);

        // 6) 启动 Javalin
        Javalin app = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.jsonMapper(new JavalinJackson());
            // sa-token 需要在线程中维护 SaTokenContext 才能读写 token；
            // 测试侧在每个请求前后初始化/清理 mock 上下文，并把 Javalin 的 header/cookie 拷贝到 mock request 中。
            cfg.routes.before(ctx -> {
                SaTokenContextMockUtil.setMockContext();
                SaRequestForMock req = (SaRequestForMock) SaHolder.getRequest();
                ctx.headerMap().forEach(req.headerMap::put);
                ctx.cookieMap().forEach((k, v) -> req.cookieMap.put(k, v));
            });
            cfg.routes.after(ctx -> SaTokenContextMockUtil.clearContext());
            cfg.routes.apiBuilder(() -> {
                authController.routes().addEndpoints();
                ApiBuilder.path("rbac", authzController.routes());
                goodsQueryResource.routes().addEndpoints();
                goodsResource.routes().addEndpoints();
            });
        });
        app.start(0);
        return app;
    }

    /**
     * \u517c\u5bb9\u5c42\uff1a\u4fdd\u7559 Guice injector \u5165\u53e3\u4f9b\u9700\u8981 SubjectProvider \u7684\u4ee3\u7801\u4f7f\u7528\uff08\u5b9e\u9645\u672a\u4f7f\u7528\uff09\u3002
     *  \u771f\u5b9e\u6d4b\u8bd5\u90fd\u901a\u8fc7 {@link #start()} \u76f4\u63a5\u6784\u9020\u5b9e\u4f8b\u3002
     *
     * @return \u6ce8\u518c\u793a\u4f8b\u8ba4\u8bc1\u4f9d\u8d56\u7684 Guice \u6ce8\u5165\u5668
     */
    @SuppressWarnings("unused")
    public static Injector createInjector() {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(UserRepository.class).toInstance(new InMemoryUserRepository());
                bind(RoleRepository.class).toInstance(new InMemoryRoleRepository());
                bind(PermissionRepository.class).toInstance(new InMemoryPermissionRepository());
                InMemoryGoodsRepository goodsRepository = new InMemoryGoodsRepository();
                bind(GoodsRepository.class).toInstance(goodsRepository);
            }
        });
    }

    /** \u628a\u9886\u57df\u4e8b\u4ef6\u4ea4\u7ed9\u53ef\u68c0\u67e5\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    private static final class NoopDomainEventPublisher implements DomainEventPublisher {
        /**
         * 提供测试替身回调，调用时保持夹具状态。
         *
         * @param <ID> 实体标识类型
         * @param event 领域事件
         */
        @Override
        public <ID extends EntityId> void publish(io.ddd4j.core.ddd.event.DomainEvent<ID> event) {
            // noop：测试不需要真实发布领域事件
        }
    }

    /** \u8fd4\u56de\u7528\u4e8e\u6821\u9a8c\u672c\u5730\u5316\u5165\u53e3\u7684\u56fa\u5b9a\u6d88\u606f\u3002 */
    private static final class NoopI18nProvider implements I18nProvider {
        /**
         * 按提供的条件读取或解析消息文本。
         *
         * @param key 消息 key
         * @param args 格式化参数
         * @return 实际返回<code>key</code>，供调用方取得当前值
         */
        @Override
        public String getMessage(String key, Object... args) {
            return key;
        }
    }
}
