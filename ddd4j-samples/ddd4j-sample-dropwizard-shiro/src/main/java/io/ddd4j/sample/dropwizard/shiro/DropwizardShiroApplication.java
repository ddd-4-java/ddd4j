package io.ddd4j.sample.dropwizard.shiro;

import com.google.common.collect.ImmutableList;
import io.ddd4j.auth.shiro.subject.ShiroSubjectProvider;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle;
import io.dropwizard.Application;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.lifecycle.Managed;
import javax.servlet.DispatcherType;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 原生 Dropwizard 认证样例；主入口与可调用入口共用全部 Bundle、资源及 Managed。
 */
public final class DropwizardShiroApplication extends Application<AuthConfiguration> {
    /** \u5f53\u524d HTTP \u793a\u4f8b\u4f7f\u7528\u7684\u5171\u7528\u5546\u54c1\u4ea4\u6613\u5165\u53e3\u3002 */
    private final CatalogEndpoints catalog;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u540e\u901a\u77e5\u6d4b\u8bd5\u6216\u5916\u90e8\u89c2\u5bdf\u8005\u7684\u56de\u8c03\u3002 */
    private final BiConsumer<String, Boolean> completed;
    /** \u7528\u4e8e\u6ce8\u518c\u6216\u5b9a\u4f4d\u7f13\u5b58\u5b9e\u4f8b\u7684\u540d\u79f0\u3002 */
    private final String cacheName = "dropwizard-shiro-" + java.util.UUID.randomUUID();
    /** \u5904\u7406\u5f53\u524d\u793a\u4f8b\u5e94\u7528\u767b\u5f55\u72b6\u6001\u7684\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6\u3002 */
    private NativeAuth auth;

    /**
     * 使用共享 MySQL 商品交易服务。
     */
    public DropwizardShiroApplication() {
        this(CatalogRuntime.start(), (id, clean) -> {
        });
    }

    /**
     * @param catalog   商品交易服务
     * @param completed 请求结束后的只读观测器
     */
    public DropwizardShiroApplication(CatalogEndpoints catalog, BiConsumer<String, Boolean> completed) {
        this.catalog = Objects.requireNonNull(catalog);
        this.completed = Objects.requireNonNull(completed);
    }

    /**
     * @param args 原生 server/config 参数
     * @throws Exception 启动失败
     */
    public static void main(String[] args) throws Exception {
        new DropwizardShiroApplication().run(args);
    }

    /**
     * \u5728 Dropwizard \u5f15\u5bfc\u9636\u6bb5\u6dfb\u52a0\u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u6240\u9700\u7684\u7ec4\u4ef6\u3002
     * @param bootstrap \u5f53\u524d Dropwizard \u5e94\u7528\u7684\u5f15\u5bfc\u5bf9\u8c61
     */
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
        bootstrap.addBundle(new Ddd4jBundle<AuthConfiguration>(ImmutableList.of(), ImmutableList.of(), new ShiroSubjectProvider(), I18nProvider.DEFAULT));
        bootstrap.addBundle(new Ddd4jDropwizardWebBundle<AuthConfiguration>(configuration -> {
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
    public void run(AuthConfiguration configuration, Environment environment) {
        environment.servlets().addFilter("native-shiro", new NativeRequestFilter(() -> auth, completed))
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
        environment.jersey().register(new CatalogTradeResource(catalog));
        environment.jersey().register(new AuthenticationResource(() -> auth));
    }
}
