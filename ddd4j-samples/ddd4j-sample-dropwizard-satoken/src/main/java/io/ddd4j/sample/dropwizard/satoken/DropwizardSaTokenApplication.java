package io.ddd4j.sample.dropwizard.satoken;

import io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;
import jakarta.servlet.DispatcherType;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 原生 Dropwizard 认证样例；主入口与可调用入口共用全部 Bundle、资源及 Managed。
 */
public final class DropwizardSaTokenApplication extends Application<AuthConfiguration> {
    private final CatalogEndpoints catalog;
    private final BiConsumer<String, Boolean> completed;
    private final String cacheName = "dropwizard-satoken-" + java.util.UUID.randomUUID();
    private NativeAuth auth;

    /**
     * 使用共享 MySQL 商品交易服务。
     */
    public DropwizardSaTokenApplication() {
        this(CatalogRuntime.start(), (id, clean) -> {
        });
    }

    /**
     * @param catalog   商品交易服务
     * @param completed 请求结束后的只读观测器
     */
    public DropwizardSaTokenApplication(CatalogEndpoints catalog, BiConsumer<String, Boolean> completed) {
        this.catalog = Objects.requireNonNull(catalog);
        this.completed = Objects.requireNonNull(completed);
    }

    /**
     * @param args 原生 server/config 参数
     * @throws Exception 启动失败
     */
    public static void main(String[] args) throws Exception {
        new DropwizardSaTokenApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<AuthConfiguration> bootstrap) {
        bootstrap.addBundle(new ConfiguredBundle<AuthConfiguration>() {
            @Override
            public void run(AuthConfiguration configuration, Environment environment) {
                environment.lifecycle().manage(new Managed() {
                    private Cache<String, Object> ownedCache;

                    @Override
                    public void start() {
                        auth = new NativeAuth();
                        try {
                            if (configuration.getDdd4jWeb().isIdempotencyEnabled()) {
                                ownedCache = CacheKit.buildLocalIfAbsent(cacheName, 300);
                            }
                        } catch (RuntimeException | Error failure) {
                            // 原生认证已启动，缓存启动失败时应释放认证资源并保留最初的异常。
                            try (NativeAuth owner = auth) {
                                auth = null;
                                throw failure;
                            }
                        }
                    }

                    @Override
                    public void stop() {
                        // 即使释放缓存失败，也要关闭本次启动注册的认证组件。
                        try (NativeAuth owner = auth) {
                            auth = null;
                            CacheKit.unregisterLocalIfOwned(cacheName, ownedCache);
                            ownedCache = null;
                        }
                    }
                });
            }
        });
        bootstrap.addBundle(new Ddd4jBundle<AuthConfiguration>(List.of(), List.of(), new SaTokenSubjectProvider(), I18nProvider.DEFAULT));
        bootstrap.addBundle(new Ddd4jDropwizardWebBundle<AuthConfiguration>(configuration -> {
            configuration.getDdd4jWeb().setIdempotencyCacheName(cacheName);
            return configuration.getDdd4jWeb();
        }));
    }

    @Override
    public void run(AuthConfiguration configuration, Environment environment) {
        environment.servlets().addFilter("native-satoken", new NativeRequestFilter(completed))
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
        environment.jersey().register(new CatalogTradeResource(catalog));
        environment.jersey().register(new AuthenticationResource(() -> auth));
    }
}
