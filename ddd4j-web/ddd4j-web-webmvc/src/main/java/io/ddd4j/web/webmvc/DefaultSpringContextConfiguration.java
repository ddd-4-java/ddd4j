/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.web.webmvc.webmvc;

import org.springframework.biz.context.SpringContextAwareContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DefaultSpringContextConfiguration {

    @Bean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }

}


