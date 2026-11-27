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
package io.ddd4j.web.webmvc.config;

import io.ddd4j.core.BaseCoreProperties;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.web.webmvc.converter.CustomHttpMessageConverter;
import io.ddd4j.web.webmvc.core.GlobalRequestAdvice;
import io.ddd4j.web.webmvc.core.GlobalResponseRAdvice;
import io.ddd4j.web.webmvc.core.GlobalRestExceptionAdvice;
import io.ddd4j.web.webmvc.interceptor.BaseWebInterceptor;
import io.ddd4j.web.webmvc.utils.LocalDateTimeFormatter;
import io.ddd4j.web.webmvc.utils.LocalTimeFormatter;
import io.ddd4j.web.webmvc.ws.BaseWebSocketServer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/**
 * Web 基础配置（Spring Boot 自动装配）。
 * <p>配置 Feign 拦截器、ObjectMapper、日期格式化、全局异常处理、WebSocket 客户端等。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
@Slf4j(topic = "### BASE-WEB : BaseWebConfig ###")
// @EnableConfigurationProperties(BaseWebProperties.class)
/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
@RequiredArgsConstructor
public class BaseWebConfig implements WebMvcConfigurer {
    /**
     * 基础 Web 拦截器列表
     */
    final List<BaseWebInterceptor> baseWebInterceptors;
    /**
     * 核心基础配置属性
     */
    final BaseCoreProperties baseCoreProperties;
    /**
     * WebSocket 服务端列表
     */
    final List<BaseWebSocketServer> baseWebSocketServers;

    /**
     * \u6ce8\u518c\u683c\u5f0f\u5316\u5668\uff08\u65e5\u671f\u3001\u65e5\u671f\u65f6\u95f4\u3001\u65f6\u95f4\uff09\u3002
     *
     * @param registry MVC \u683c\u5f0f\u5316\u5668\u6ce8\u518c\u5668
     */
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new DateFormatter("yyyy-MM-dd HH:mm:ss"));
        registry.addFormatter(new LocalDateTimeFormatter("yyyy-MM-dd HH:mm:ss"));
        registry.addFormatter(new LocalTimeFormatter("HH:mm:ss"));
    }

    /**
     * MVC ObjectMapper\uff08\u81ea\u5b9a\u4e49\u65e5\u671f\u65f6\u95f4\u683c\u5f0f\uff09\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public ObjectMapper mvcObjectMapper() {
        log.debug("Loading mvcObjectMapper");
        return JsonKit.buildObjectMapper(baseCoreProperties.getDatePattern(), baseCoreProperties.getDateTimePattern(), baseCoreProperties.getTimePattern());
    }

    /**
     * \u81ea\u5b9a\u4e49 Jackson 2 JSON \u6d88\u606f\u8f6c\u6362\u5668\u3002
     *  <p>2.0.x \u4f7f\u7528 Jackson 2 ObjectMapper\uff1b\u672c Bean \u907f\u514d\u5c06 MVC \u8f6c\u6362\u5668\u5b9e\u73b0
     *  \u4e0e Spring \u9ed8\u8ba4\u8f6c\u6362\u5668\u7684\u5185\u90e8\u914d\u7f6e\u8026\u5408\u3002</p>
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public CustomHttpMessageConverter customHttpMessageConverter() {
        log.debug("Loading customHttpMessageConverter");
        CustomHttpMessageConverter converter = new CustomHttpMessageConverter(mvcObjectMapper());
        converter.setDefaultCharset(Charset.defaultCharset());
        return converter;
    }

    /**
     * \u5168\u5c40 REST \u5f02\u5e38\u901a\u77e5\u5904\u7406\u5668\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public GlobalRestExceptionAdvice globalRestExceptionAdvice() {
        log.debug("Loading globalRestExceptionAdvice");
        return new GlobalRestExceptionAdvice();
    }

    /**
     * \u5168\u5c40\u8bf7\u6c42\u4f53\u901a\u77e5\u5904\u7406\u5668\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public GlobalRequestAdvice globalRequestAdvice() {
        log.debug("Loading globalRequestAdvice");
        return new GlobalRequestAdvice();
    }

    /**
     * \u5168\u5c40\u54cd\u5e94\u5305\u88c5\u901a\u77e5\u5904\u7406\u5668\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public GlobalResponseRAdvice globalResponseRAdvice() {
        log.debug("Loading globalResponseRAdvice");
        return new GlobalResponseRAdvice();
    }

    /**
     * \u6ce8\u518c\u62e6\u622a\u5668\u3002
     *
     * @param registry MVC \u8bf7\u6c42\u62e6\u622a\u5668\u6ce8\u518c\u5668
     */
    public void addInterceptors(InterceptorRegistry registry) {
        if (Objects.nonNull(baseWebInterceptors) && !baseWebInterceptors.isEmpty()) {
            baseWebInterceptors.forEach(baseInterceptor -> {
                log.debug("Loading {}", baseInterceptor.getClass().getSimpleName());
                registry.addInterceptor(baseInterceptor).addPathPatterns(baseInterceptor.pathPatterns()).excludePathPatterns(baseInterceptor.excludePathPatterns());
            });
        } else {
            /**
             * log.warn() 方法。
             * @param empty!" empty!" 参数。
             */
            log.warn("baseWebInterceptors is empty!");
        }
    }

    /**
     * 执行初始化。
     */
    @PostConstruct
    public void init() {
        // 启动WebSocket服务端
        if (!baseWebSocketServers.isEmpty()) {
            for (BaseWebSocketServer server : baseWebSocketServers) {
//                WebSocketKit.startServer(server.getClass());
            }
        }
    }
}
