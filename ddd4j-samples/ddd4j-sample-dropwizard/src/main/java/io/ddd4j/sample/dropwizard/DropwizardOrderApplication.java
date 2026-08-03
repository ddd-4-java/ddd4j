package io.ddd4j.sample.dropwizard;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;

/**
 * 共享 Order 业务内核的 Dropwizard 启动入口。
 */
public final class DropwizardOrderApplication extends Application<DropwizardOrderConfiguration> {

    private static final String IDEMPOTENCY_CACHE_NAME = "ddd4j-web-idempotency";
    private static final long IDEMPOTENCY_CACHE_TTL_SECONDS = 300L;

    private final InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
    private final OrderApplicationService applicationService = new OrderApplicationService(adapters, adapters, adapters,
            adapters, adapters);

    public static void main(String[] args) throws Exception {
        new DropwizardOrderApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<DropwizardOrderConfiguration> bootstrap) {
        bootstrap.addBundle(new Ddd4jBundle<>());
        bootstrap.addBundle(new Ddd4jDropwizardWebBundle<>(DropwizardOrderConfiguration::getDdd4jWeb));
    }

    @Override
    public void run(DropwizardOrderConfiguration configuration, Environment environment) {
        // 本地示例仅用于验证幂等租约协议；生产环境应注册共享 Redis 或 Redisson CAS 缓存。
        environment.lifecycle().manage(new Managed() {
            @Override
            public void start() {
                CacheKit.build(IDEMPOTENCY_CACHE_NAME, IDEMPOTENCY_CACHE_TTL_SECONDS);
            }

            @Override
            public void stop() {
                CacheKit.unregister(IDEMPOTENCY_CACHE_NAME);
            }
        });
        environment.jersey().register(new DropwizardOrderResource(applicationService));
    }
}
