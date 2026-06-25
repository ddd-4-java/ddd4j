package io.ddd4j.boot.web.config;

import io.ddd4j.boot.core.config.BaseCoreProperties;
import io.ddd4j.boot.core.context.SpringContext;
import io.ddd4j.boot.core.utils.JsonKit;
import io.ddd4j.boot.web.core.GlobalRequestAdvice;
import io.ddd4j.boot.web.core.GlobalResponseRAdvice;
import io.ddd4j.boot.web.core.GlobalRestExceptionAdvice;
import io.ddd4j.boot.web.interceptor.BaseWebInterceptor;
import io.ddd4j.boot.web.interceptor.FeignHeaderInterceptor;
import io.ddd4j.boot.web.utils.BaseWebSocketServer;
import io.ddd4j.boot.web.utils.LocalDateTimeFormatter;
import io.ddd4j.boot.web.utils.LocalTimeFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import jakarta.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.List;

@Configuration
@Slf4j(topic = "### BASE-WEB : BaseWebConfig ###")
// @EnableConfigurationProperties(BaseWebProperties.class)
@RequiredArgsConstructor
public class BaseWebConfig implements WebMvcConfigurer {
    final List<BaseWebInterceptor> baseWebInterceptors;
    final BaseCoreProperties baseCoreProperties;
    final List<BaseWebSocketServer> baseWebSocketServers;

    @Bean
    // @ConditionalOnClass(Feign.class)
    public RequestInterceptor feignHeaderInterceptor() {
        log.debug("Loading feignHeaderInterceptor");
        return new FeignHeaderInterceptor();
    }

    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new DateFormatter("yyyy-MM-dd HH:mm:ss"));
        registry.addFormatter(new LocalDateTimeFormatter("yyyy-MM-dd HH:mm:ss"));
        registry.addFormatter(new LocalTimeFormatter("HH:mm:ss"));
    }

    @Bean
    // @ConditionalOnMissingBean
    public ObjectMapper mvcObjectMapper() {
        log.debug("Loading mvcObjectMapper");
        return JsonKit.buildObjectMapper(baseCoreProperties.getDatePattern(), baseCoreProperties.getDateTimePattern(), baseCoreProperties.getTimePattern());
    }

    @Bean
    // @ConditionalOnMissingBean
    public MappingJackson2HttpMessageConverter jackson2HttpMessageConverter() {
        log.debug("Loading jackson2HttpMessageConverter");
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setDefaultCharset(Charset.defaultCharset());
        converter.setObjectMapper(mvcObjectMapper());
        return converter;
    }

    @Bean
    // @ConditionalOnMissingBean
    public SpringContext springContext() {
        return new SpringContext();
    }

    @Bean
    // @ConditionalOnMissingBean
    public GlobalRestExceptionAdvice globalRestExceptionAdvice() {
        log.debug("Loading globalRestExceptionAdvice");
        return new GlobalRestExceptionAdvice();
    }

    @Bean
    // @ConditionalOnMissingBean
    public GlobalRequestAdvice globalRequestAdvice() {
        log.debug("Loading globalRequestAdvice");
        return new GlobalRequestAdvice();
    }

    @Bean
    // @ConditionalOnMissingBean
    public GlobalResponseRAdvice globalResponseRAdvice() {
        log.debug("Loading globalResponseRAdvice");
        return new GlobalResponseRAdvice();
    }

    public void addInterceptors(InterceptorRegistry registry) {
        if (baseWebInterceptors != null && !baseWebInterceptors.isEmpty()) {
            baseWebInterceptors.forEach(baseInterceptor -> {
                log.debug("Loading {}", baseInterceptor.getClass().getSimpleName());
                registry.addInterceptor(baseInterceptor).addPathPatterns(baseInterceptor.pathPatterns()).excludePathPatterns(baseInterceptor.excludePathPatterns());
            });
        } else {
            log.warn("baseWebInterceptors is empty!");
        }
    }

    @Bean
    public WebSocketClient webSocketClient() {
        return new StandardWebSocketClient();
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