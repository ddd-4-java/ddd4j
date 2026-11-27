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

import io.ddd4j.dropwizard.Ddd4jLogbackAccessRequestLogFactory;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import java.util.Objects;
import java.util.function.Function;

/**
 * 注册 ddd4j Jersey 过滤器与异常映射器的 Dropwizard Bundle。
 */
public final class Ddd4jDropwizardWebBundle<C extends Configuration> implements ConfiguredBundle<C> {

    /** \u4ece Dropwizard \u5e94\u7528\u914d\u7f6e\u53d6\u5f97 DDD4J Web \u914d\u7f6e\u7684\u51fd\u6570\u3002 */
    private final Function<C, Ddd4jDropwizardWebConfiguration> configurationResolver;

    /** 使用默认 Web 配置解析器创建 Bundle。 */
    public Ddd4jDropwizardWebBundle() {
        this(configuration -> new Ddd4jDropwizardWebConfiguration());
    }

    /**
     * 使用指定 Web 配置解析器创建 Bundle。
     *
     * @param configurationResolver 从应用配置获取 Web 配置的函数
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
        if (Objects.nonNull(configuration)) {
            Ddd4jLogbackAccessRequestLogFactory.configure(configuration);
        }
        environment.jersey().register(new Ddd4jDropwizardRequestFilter(webConfiguration));
        environment.jersey().register(new Ddd4jDropwizardResponseFilter());
        Ddd4jDropwizardExceptionMapper exceptionMapper = new Ddd4jDropwizardExceptionMapper();
        environment.jersey().register(exceptionMapper);
        environment.jersey().register(new Ddd4jDropwizardIllegalStateExceptionMapper(exceptionMapper));
    }
}
