package io.ddd4j.web.dropwizard;

import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import java.util.Objects;
import java.util.function.Function;

/**
 * \u6ce8\u518c ddd4j Jersey \u8fc7\u6ee4\u5668\u4e0e\u5f02\u5e38\u6620\u5c04\u5668\u7684 Dropwizard Bundle\u3002
 *
 * @param <C> 由 Ddd4jDropwizardWebBundle 处理的泛型类型
 */
public final class Ddd4jDropwizardWebBundle<C extends Configuration> implements ConfiguredBundle<C> {

    /** \u4ece Dropwizard \u5e94\u7528\u914d\u7f6e\u53d6\u5f97 DDD4J Web \u914d\u7f6e\u7684\u51fd\u6570\u3002 */
    private final Function<C, Ddd4jDropwizardWebConfiguration> configurationResolver;

    /** \u4f7f\u7528\u9ed8\u8ba4 Web \u914d\u7f6e\u89e3\u6790\u5668\u521b\u5efa Bundle\u3002 */
    public Ddd4jDropwizardWebBundle() {
        this(configuration -> new Ddd4jDropwizardWebConfiguration());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a Web \u914d\u7f6e\u89e3\u6790\u5668\u521b\u5efa Bundle\u3002
     * @param configurationResolver \u4ece\u5e94\u7528\u914d\u7f6e\u83b7\u53d6 Web \u914d\u7f6e\u7684\u51fd\u6570
     */
    public Ddd4jDropwizardWebBundle(Function<C, Ddd4jDropwizardWebConfiguration> configurationResolver) {
        this.configurationResolver = Objects.requireNonNull(configurationResolver,
                "configurationResolver must not be null");
    }

    /**
     * \u63d0\u4f9b Web Bundle \u7684\u6846\u67b6\u5f15\u5bfc\u521d\u59cb\u5316\u5165\u53e3\u3002
     * @param bootstrap Dropwizard \u542f\u52a8\u5f15\u5bfc\u5bf9\u8c61
     */
    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        // Dropwizard Web 接线只依赖 run 阶段已经构造完成的 Jersey Environment。
    }

    /**
     * \u5728\u5df2\u542f\u52a8\u7684 Dropwizard \u5e94\u7528\u4e2d\u88c5\u914d\u8bf7\u6c42\u548c\u54cd\u5e94\u5904\u7406\u7ec4\u4ef6\u3002
     * @param configuration \u5f53\u524d\u5e94\u7528\u7684\u914d\u7f6e\u5bf9\u8c61
     * @param environment \u5f53\u524d Dropwizard \u5e94\u7528\u73af\u5883
     */
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
