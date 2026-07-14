package io.ddd4j.web.dropwizard;

import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import java.util.Objects;
import java.util.function.Function;

/**
 * 注册 ddd4j Jersey 过滤器与异常映射器的 Dropwizard Bundle。
 */
public final class Ddd4jDropwizardWebBundle<C extends Configuration> implements ConfiguredBundle<C> {

    private final Function<C, Ddd4jDropwizardWebConfiguration> configurationResolver;

    public Ddd4jDropwizardWebBundle() {
        this(configuration -> new Ddd4jDropwizardWebConfiguration());
    }

    public Ddd4jDropwizardWebBundle(Function<C, Ddd4jDropwizardWebConfiguration> configurationResolver) {
        this.configurationResolver = Objects.requireNonNull(configurationResolver,
                "configurationResolver must not be null");
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        // Dropwizard Web 接线只依赖 run 阶段已经构造完成的 Jersey Environment。
    }

    @Override
    public void run(C configuration, Environment environment) {
        Ddd4jDropwizardWebConfiguration webConfiguration = Objects.requireNonNull(
                configurationResolver.apply(configuration), "web configuration must not be null");
        environment.jersey().register(new Ddd4jDropwizardRequestFilter(webConfiguration));
        environment.jersey().register(new Ddd4jDropwizardResponseFilter());
        Ddd4jDropwizardExceptionMapper exceptionMapper = new Ddd4jDropwizardExceptionMapper();
        environment.jersey().register(exceptionMapper);
        environment.jersey().register(new Ddd4jDropwizardIllegalStateExceptionMapper(exceptionMapper));
    }
}
