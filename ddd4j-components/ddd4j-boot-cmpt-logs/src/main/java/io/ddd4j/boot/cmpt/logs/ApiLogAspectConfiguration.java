/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.cmpt.logs;

import io.ddd4j.boot.cmpt.logs.aspect.ApiOperationLogProvider;
import io.ddd4j.boot.cmpt.logs.aspect.DefaultApiOperationLogProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiLogAspectConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiOperationLogProvider apiOperationLogProvider() {
        return new DefaultApiOperationLogProvider();
    }

}
