/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.logs;

import io.ddd4j.data.logs.aspect.ApiOperationLogProvider;
import io.ddd4j.data.logs.aspect.DefaultApiOperationLogProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiLogAspectConfiguration {

    @Bean
    public ApiOperationLogProvider apiOperationLogProvider() {
        return new DefaultApiOperationLogProvider();
    }

}
