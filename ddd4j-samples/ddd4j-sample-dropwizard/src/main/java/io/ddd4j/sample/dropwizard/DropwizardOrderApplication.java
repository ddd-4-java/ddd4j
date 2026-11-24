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

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;

import java.util.UUID;

/**
 * 共享 Order 业务内核的 Dropwizard 启动入口。
 */
public final class DropwizardOrderApplication extends Application<DropwizardOrderConfiguration> {

    private final String cacheName = "dropwizard-order-" + UUID.randomUUID();
    private static final long IDEMPOTENCY_CACHE_TTL_SECONDS = 300L;

    private final InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
    private final OrderApplicationService applicationService = new OrderApplicationService(adapters, adapters, adapters,
            adapters, adapters);

    /**
     * @param args 启动参数
     * @throws Exception 启动失败时抛出
     */
    public static void main(String[] args) throws Exception {
        new DropwizardOrderApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<DropwizardOrderConfiguration> bootstrap) {
        bootstrap.addBundle(new Ddd4jBundle<>());
        bootstrap.addBundle(new Ddd4jDropwizardWebBundle<DropwizardOrderConfiguration>(configuration -> {
            configuration.getDdd4jWeb().setIdempotencyCacheName(cacheName);
            return configuration.getDdd4jWeb();
        }));
    }

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
        environment.jersey().register(new DropwizardOrderResource(applicationService));
    }
}
