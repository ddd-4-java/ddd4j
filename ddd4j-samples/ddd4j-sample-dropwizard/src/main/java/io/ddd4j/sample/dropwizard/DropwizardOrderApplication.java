package io.ddd4j.sample.dropwizard;

import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

/**
 * 共享 Order 业务内核的 Dropwizard 启动入口。
 */
public final class DropwizardOrderApplication extends Application<DropwizardOrderConfiguration> {

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
        environment.jersey().register(new DropwizardOrderResource(applicationService));
    }
}
