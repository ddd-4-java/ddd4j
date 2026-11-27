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
package io.ddd4j.sample.dropwizard;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.lifecycle.Managed;

import java.util.UUID;

/**
 * 共享 Order 业务内核的 Dropwizard 启动入口。
 */
public final class DropwizardOrderApplication extends Application<DropwizardOrderConfiguration> {

    /** \u5e42\u7b49\u7f13\u5b58\u6761\u76ee\u5b58\u6d3b\u65f6\u95f4\uff0c\u5355\u4f4d\u4e3a\u79d2\u3002 */
    private static final long IDEMPOTENCY_CACHE_TTL_SECONDS = 300L;
    /** \u7528\u4e8e\u6ce8\u518c\u6216\u5b9a\u4f4d\u7f13\u5b58\u5b9e\u4f8b\u7684\u540d\u79f0\u3002 */
    private final String cacheName = "dropwizard-order-" + UUID.randomUUID();

    /**
     * @param args 启动参数
     * @throws Exception 启动失败时抛出
     */
    public static void main(String[] args) throws Exception {
        new DropwizardOrderApplication().run(args);
    }

    /**
     * \u5728 Dropwizard \u5f15\u5bfc\u9636\u6bb5\u6dfb\u52a0\u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u6240\u9700\u7684\u7ec4\u4ef6\u3002
     * @param bootstrap \u5f53\u524d Dropwizard \u5e94\u7528\u7684\u5f15\u5bfc\u5bf9\u8c61
     */
    @Override
    public void initialize(Bootstrap<DropwizardOrderConfiguration> bootstrap) {
        bootstrap.addBundle(new Ddd4jBundle<>());
        bootstrap.addBundle(new Ddd4jDropwizardWebBundle<DropwizardOrderConfiguration>(configuration -> {
            configuration.getDdd4jWeb().setIdempotencyCacheName(cacheName);
            return configuration.getDdd4jWeb();
        }));
    }

    /**
     * \u4ece\u914d\u7f6e\u88c5\u914d\u5546\u54c1\u4ea4\u6613\u5e94\u7528\u5e76\u5411 Dropwizard \u73af\u5883\u6ce8\u518c\u8d44\u6e90\u3002
     * @param configuration \u5f53\u524d\u5e94\u7528\u52a0\u8f7d\u7684\u8ba4\u8bc1\u6216\u4ea4\u6613\u914d\u7f6e
     * @param environment \u7528\u4e8e\u767b\u8bb0\u8def\u7531\u3001\u8fc7\u6ee4\u5668\u53ca\u751f\u547d\u5468\u671f\u7684\u6846\u67b6\u73af\u5883
     */
    @Override
    public void run(DropwizardOrderConfiguration configuration, Environment environment) {
        if (configuration.getDdd4jWeb().isIdempotencyEnabled()) {
            // 缓存只归创建它的此应用实例所有。
            environment.lifecycle().manage(new Managed() {
                private Cache<String, Object> ownedCache;

                @Override
                public void start() {
                    ownedCache = CacheKit.buildLocalIfAbsent(cacheName, IDEMPOTENCY_CACHE_TTL_SECONDS);
                }

                @Override
                public void stop() {
                    CacheKit.unregisterLocalIfOwned(cacheName, ownedCache);
                }
            });
        }
        CatalogApi api = StrKit.isBlank(configuration.getCatalogJdbcUrl()) ? CatalogRuntime.start()
                : CatalogRuntime.start(configuration.getCatalogJdbcUrl(), configuration.getCatalogDbUser(),
                configuration.getCatalogDbPassword());
        environment.jersey().register(new CatalogTradeResource(api));
        environment.jersey().register(new AuthenticationResource());
    }
}
