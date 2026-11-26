package io.ddd4j.sample.dropwizard.cqrs;

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

/**
 * Dropwizard 四表商品交易及快照投影。
 */
public final class DropwizardCqrsApplication extends Application<DropwizardCqrsConfiguration> {
    public static void main(String[] args) throws Exception {
        new DropwizardCqrsApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<DropwizardCqrsConfiguration> bootstrap) {
        bootstrap.addBundle(new Ddd4jBundle<>());
        bootstrap.addBundle(new Ddd4jDropwizardWebBundle<DropwizardCqrsConfiguration>(configuration -> {
            var web = new Ddd4jDropwizardWebConfiguration();
            web.setDefaultAuthenticationMode(AuthenticationMode.DISABLED);
            web.setIdempotencyEnabled(false);
            return web;
        }));
    }

    @Override
    public void run(DropwizardCqrsConfiguration config, Environment environment) {
        Ddd4jLogbackAccessRequestLogFactory.configure(config);
        var api = StrKit.isBlank(config.getCatalogJdbcUrl()) ? CatalogRuntime.start()
                : CatalogRuntime.start(config.getCatalogJdbcUrl(), config.getCatalogDbUser(), config.getCatalogDbPassword());
        environment.jersey().register(new CatalogTradeResource(api));
    }
}
