package io.ddd4j.auth.datascope;

import org.springframework.biz.context.SpringContextAwareContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DataScopeAutoConfiguration {

    @Bean
    public DataScopeProvider dataScopeProvider() {
        // 默认提供者：沿用既有语义——非空数据视为有权限
        return (dataType, data) -> java.util.Objects.nonNull(data);
    }

    @Bean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }

}
