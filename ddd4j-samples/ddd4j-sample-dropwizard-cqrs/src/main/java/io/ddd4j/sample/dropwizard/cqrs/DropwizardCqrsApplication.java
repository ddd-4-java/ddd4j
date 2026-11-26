package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dropwizard 四表商品交易及快照投影。
 */
public final class DropwizardCqrsApplication extends Application<DropwizardCqrsConfiguration> {
    private final List<Consumer<Object>> listeners;

    public DropwizardCqrsApplication() {
        this.listeners = Collections.emptyList();
    }

    DropwizardCqrsApplication(Consumer<Object> listener) {
        this.listeners = Collections.singletonList(listener);
    }

    public static void main(String[] args) throws Exception {
        new DropwizardCqrsApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<DropwizardCqrsConfiguration> bootstrap) {
        bootstrap.addBundle(new Ddd4jBundle<>(Collections.emptyList(), listeners, null, I18nProvider.DEFAULT));
        bootstrap.addBundle(new Ddd4jDropwizardWebBundle<DropwizardCqrsConfiguration>(configuration -> {
            Ddd4jDropwizardWebConfiguration web = new Ddd4jDropwizardWebConfiguration();
            web.setDefaultAuthenticationMode(AuthenticationMode.DISABLED);
            web.setIdempotencyEnabled(false);
            return web;
        }));
    }

    @Override
    public void run(DropwizardCqrsConfiguration config, Environment environment) {
        CatalogApi api = StrKit.isBlank(config.getCatalogJdbcUrl()) ? CatalogRuntime.start()
                : CatalogRuntime.start(config.getCatalogJdbcUrl(), config.getCatalogDbUser(), config.getCatalogDbPassword());
        environment.jersey().register(new CatalogTradeResource(api));
    }
}
