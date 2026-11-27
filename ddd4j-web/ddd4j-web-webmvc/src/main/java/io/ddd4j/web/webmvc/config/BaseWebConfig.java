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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
     * Jackson JSON \u6d88\u606f\u8f6c\u6362\u5668\u3002
     *
     * @return 返回表达式 <code>converter</code>
     */
    @Bean
    public MappingJackson2HttpMessageConverter jackson2HttpMessageConverter() {
        log.debug("Loading jackson2HttpMessageConverter");
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setDefaultCharset(Charset.defaultCharset());
        converter.setObjectMapper(mvcObjectMapper());
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
            log.warn("baseWebInterceptors is empty!");
        }
    }

    /** \u521d\u59cb\u5316 Web MVC \u914d\u7f6e\u3002 */
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
