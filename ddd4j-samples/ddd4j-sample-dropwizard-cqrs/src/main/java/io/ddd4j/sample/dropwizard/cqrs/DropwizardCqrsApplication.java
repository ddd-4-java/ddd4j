package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.dropwizard.Ddd4jLogbackAccessRequestLogFactory;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dropwizard 四表商品交易及快照投影。
 */
public final class DropwizardCqrsApplication extends Application<DropwizardCqrsConfiguration> {
    /** \u6d88\u8d39\u5546\u54c1\u4ea4\u6613\u9886\u57df\u4e8b\u4ef6\u7684\u5916\u90e8\u76d1\u542c\u56de\u8c03\u3002 */
    private final List<Consumer<Object>> listeners;

    /** \u6784\u9020\u6ca1\u6709\u5916\u90e8\u4e8b\u4ef6\u76d1\u542c\u5668\u7684 CQRS \u793a\u4f8b\u5e94\u7528\u3002 */
    public DropwizardCqrsApplication() {
        this.listeners = Collections.emptyList();
    }

    /**
     * \u521b\u5efa\u5411\u6307\u5b9a\u6d4b\u8bd5\u6216\u5e94\u7528\u76d1\u542c\u5668\u8f6c\u53d1\u4ea4\u6613\u4e8b\u4ef6\u7684 CQRS \u5e94\u7528\u3002
     * @param listener \u63a5\u6536\u5f53\u524d\u793a\u4f8b\u53d1\u5e03\u7684\u4ea4\u6613\u4e8b\u4ef6\u7684\u76d1\u542c\u5668
     */
    DropwizardCqrsApplication(Consumer<Object> listener) {
        this.listeners = Collections.singletonList(listener);
    }

    /**
     * \u5c06\u547d\u4ee4\u884c\u53c2\u6570\u4ea4\u7ed9 Dropwizard\uff0c\u8fd0\u884c\u5546\u54c1\u4ea4\u6613 CQRS \u793a\u4f8b\u3002
     * @param args \u5e94\u7528\u542f\u52a8\u53c2\u6570
     * @throws Exception Dropwizard \u5e94\u7528\u521d\u59cb\u5316\u6216\u542f\u52a8\u5931\u8d25
     */
    public static void main(String[] args) throws Exception {
        new DropwizardCqrsApplication().run(args);
    }

    /**
     * \u5728 Dropwizard \u5f15\u5bfc\u9636\u6bb5\u6dfb\u52a0\u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u6240\u9700\u7684\u7ec4\u4ef6\u3002
     * @param bootstrap \u5f53\u524d Dropwizard \u5e94\u7528\u7684\u5f15\u5bfc\u5bf9\u8c61
     */
    @Override
    public void initialize(Bootstrap<DropwizardCqrsConfiguration> bootstrap) {
        bootstrap.addBundle(new Ddd4jBundle<>(Collections.emptyList(), listeners, null, I18nProvider.DEFAULT));
        bootstrap.addBundle(new Ddd4jDropwizardWebBundle<DropwizardCqrsConfiguration>(configuration -> {
            var web = new Ddd4jDropwizardWebConfiguration();
            web.setDefaultAuthenticationMode(AuthenticationMode.DISABLED);
            web.setIdempotencyEnabled(false);
            return web;
        }));
    }

    /**
     * \u4ece\u914d\u7f6e\u88c5\u914d\u5546\u54c1\u4ea4\u6613\u5e94\u7528\u5e76\u5411 Dropwizard \u73af\u5883\u6ce8\u518c\u8d44\u6e90\u3002
     * @param config \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u7684\u6570\u636e\u5e93\u53ca\u670d\u52a1\u914d\u7f6e
     * @param environment \u7528\u4e8e\u767b\u8bb0\u8def\u7531\u3001\u8fc7\u6ee4\u5668\u53ca\u751f\u547d\u5468\u671f\u7684\u6846\u67b6\u73af\u5883
     */
    @Override
    public void run(DropwizardCqrsConfiguration config, Environment environment) {
        Ddd4jLogbackAccessRequestLogFactory.configure(config);
        var api = StrKit.isBlank(config.getCatalogJdbcUrl()) ? CatalogRuntime.start()
                : CatalogRuntime.start(config.getCatalogJdbcUrl(), config.getCatalogDbUser(), config.getCatalogDbPassword());
        environment.jersey().register(new CatalogTradeResource(api));
    }
}
