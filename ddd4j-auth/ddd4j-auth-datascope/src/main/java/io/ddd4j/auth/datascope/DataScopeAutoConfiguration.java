package io.ddd4j.auth.datascope;

import org.springframework.biz.context.SpringContextAwareContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DataScopeAutoConfiguration {

    @Bean
    public DataScopeProvider dataScopeProvider() {
        return new DataScopeProvider() {
        };
    }

    @Bean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }

}
