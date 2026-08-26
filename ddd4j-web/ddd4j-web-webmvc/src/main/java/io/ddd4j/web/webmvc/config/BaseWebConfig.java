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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.BaseCoreProperties;
import io.ddd4j.kit.lang.JsonKit;
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
import io.ddd4j.web.webmvc.converter.Jackson3HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
     * 注册格式化器（日期、日期时间、时间）。
     */
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new DateFormatter("yyyy-MM-dd HH:mm:ss"));
        registry.addFormatter(new LocalDateTimeFormatter("yyyy-MM-dd HH:mm:ss"));
        registry.addFormatter(new LocalTimeFormatter("HH:mm:ss"));
    }

    /**
     * MVC ObjectMapper（自定义日期时间格式）。
     */
    @Bean
    public ObjectMapper mvcObjectMapper() {
        log.debug("Loading mvcObjectMapper");
        return JsonKit.buildObjectMapper(baseCoreProperties.getDatePattern(), baseCoreProperties.getDateTimePattern(), baseCoreProperties.getTimePattern());
    }

    /**
     * Jackson 3 JSON 消息转换器。
     * <p>Spring 7 的 MappingJackson2HttpMessageConverter 类名保留向后兼容但
     * setObjectMapper 仍要求 Jackson 2 ObjectMapper，使用 Jackson3HttpMessageConverter 桥接。</p>
     */
    @Bean
    public Jackson3HttpMessageConverter jackson3HttpMessageConverter() {
        log.debug("Loading jackson3HttpMessageConverter");
        Jackson3HttpMessageConverter converter = new Jackson3HttpMessageConverter(mvcObjectMapper());
        converter.setDefaultCharset(Charset.defaultCharset());
        return converter;
    }

    /**
     * 全局 REST 异常通知处理器。
     */
    @Bean
    public GlobalRestExceptionAdvice globalRestExceptionAdvice() {
        log.debug("Loading globalRestExceptionAdvice");
        return new GlobalRestExceptionAdvice();
    }

    /**
     * 全局请求体通知处理器。
     */
    @Bean
    public GlobalRequestAdvice globalRequestAdvice() {
        log.debug("Loading globalRequestAdvice");
        return new GlobalRequestAdvice();
    }

    /**
     * 全局响应包装通知处理器。
     */
    @Bean
    public GlobalResponseRAdvice globalResponseRAdvice() {
        log.debug("Loading globalResponseRAdvice");
        return new GlobalResponseRAdvice();
    }

    /**
     * 注册拦截器。
     */
    public void addInterceptors(InterceptorRegistry registry) {
        if (Objects.nonNull(baseWebInterceptors) && !baseWebInterceptors.isEmpty()) {
            baseWebInterceptors.forEach(baseInterceptor -> {
                log.debug("Loading {}", baseInterceptor.getClass().getSimpleName());
                registry.addInterceptor(baseInterceptor).addPathPatterns(baseInterceptor.pathPatterns()).excludePathPatterns(baseInterceptor.excludePathPatterns());
            });
        } else {
            log.warn("baseWebInterceptors is empty!");
        }
    }

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
