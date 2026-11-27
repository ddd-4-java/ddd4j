/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.guice.config;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Guice 属性加载器（等价于 Spring 的 PropertySourcePostProcessor）。
 * <p>
 * 加载 classpath 下的 ddd4j-default.properties 文件，并通过
 * {@link Names#bindProperties(com.google.inject.Binder, Properties)} 绑定到 Guice Injector。
 * <p>
 * 业务模块可在自己的 Module 中 install(new GuicePropertyLoader("my-config.properties")) 加载自定义属性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class GuicePropertyLoader extends AbstractModule {

    /**
     * 默认配置文件路径
     */
    private static final String DEFAULT_CONFIG = "ddd4j-default.properties";

    /**
     * 待加载的配置文件路径列表
     */
    private final String[] configFiles;

    /**
     * 加载默认配置文件
     */
    public GuicePropertyLoader() {
        this(DEFAULT_CONFIG);
    }

    /**
     * 加载指定配置文件
     *
     * @param configFiles 配置文件路径（classpath 下）
     */
    public GuicePropertyLoader(String... configFiles) {
        this.configFiles = configFiles;
    }

    /** \u4e3a Guice \u5e94\u7528\u52a0\u8f7d\u5e76\u7ed1\u5b9a\u914d\u7f6e\u5c5e\u6027\u3002 */
    @Override
    protected void configure() {
        Properties merged = new Properties();
        for (String configFile : configFiles) {
            Properties props = loadProperties(configFile);
            if (Objects.nonNull(props)) {
                merged.putAll(props);
                log.info("Loaded {} properties from {}", props.size(), configFile);
            }
        }
        if (!merged.isEmpty()) {
            Names.bindProperties(binder(), merged);
            log.info("Total {} properties bound to Guice Injector", merged.size());
        }
    }

    /**
     * \u4ece\u914d\u7f6e\u8d44\u6e90\u52a0\u8f7d\u5f85\u6ce8\u5165\u7684\u5c5e\u6027\u3002
     * @param resourcePath \u8981\u52a0\u8f7d\u7684\u7c7b\u8def\u5f84\u5c5e\u6027\u6587\u4ef6
     * @return \u8d44\u6e90\u4e2d\u7684\u914d\u7f6e\u5c5e\u6027
     */
    private Properties loadProperties(String resourcePath) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (Objects.isNull(is)) {
                log.debug("Config file not found: {}", resourcePath);
                return null;
            }
            Properties props = new Properties();
            props.load(is);
            return props;
        } catch (IOException e) {
            log.warn("Failed to load config file: {}", resourcePath, e);
            return null;
        }
    }

}
