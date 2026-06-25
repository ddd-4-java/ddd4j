package io.ddd4j.boot.cmpt.datascope;

import org.springframework.biz.context.SpringContextAwareContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DataScopeAutoConfiguration {

    @Bean
    // @ConditionalOnMissingBean
    public DataScopeProvider dataScopeProvider() {
        return new DataScopeProvider() {
        };
    }

    @Bean
    // @ConditionalOnMissingBean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }

}
