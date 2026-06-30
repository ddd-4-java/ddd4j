package io.ddd4j.web.webmvc.config;

import io.ddd4j.web.webmvc.interceptor.InternalAccessInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration for internal bearer-token allow-list.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
public class InternalAccessWebMvcConfig implements WebMvcConfigurer {

    private final Environment environment;

    public InternalAccessWebMvcConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public InternalAccessProperties internalAccessProperties() {
        return InternalAccessProperties.from(environment);
    }

    @Bean
    public InternalAccessInterceptor internalAccessInterceptor(InternalAccessProperties properties) {
        return new InternalAccessInterceptor(properties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalAccessInterceptor(internalAccessProperties()))
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }
}
