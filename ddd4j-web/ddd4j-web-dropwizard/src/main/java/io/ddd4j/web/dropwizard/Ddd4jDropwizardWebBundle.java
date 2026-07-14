package io.ddd4j.web.dropwizard;

import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

/**
 * 注册 ddd4j Jersey 过滤器与异常映射器的 Dropwizard Bundle。
 */
public final class Ddd4jDropwizardWebBundle<C extends Configuration> implements ConfiguredBundle<C> {

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        // Dropwizard Web 接线只依赖 run 阶段已经构造完成的 Jersey Environment。
    }

    @Override
    public void run(C configuration, Environment environment) {
        environment.jersey().register(new Ddd4jDropwizardRequestFilter());
        environment.jersey().register(new Ddd4jDropwizardResponseFilter());
        environment.jersey().register(new Ddd4jDropwizardExceptionMapper());
    }
}
