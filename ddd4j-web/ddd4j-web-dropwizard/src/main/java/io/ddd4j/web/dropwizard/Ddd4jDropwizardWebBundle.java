/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.dropwizard;

import io.dropwizard.core.Configuration;
import io.dropwizard.core.ConfiguredBundle;
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
     * initialize() 方法。
     * @param bootstrap bootstrap 参数。
     */
    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        // Dropwizard Web 接线只依赖 run 阶段已经构造完成的 Jersey Environment。
    }

    /**
     * 执行主流程。
     * @param configuration configuration 参数。
     * @param environment environment 参数。
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
