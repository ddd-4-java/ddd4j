/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.cmpt.shiro;

import io.ddd4j.boot.cmpt.shiro.subject.ShiroSubjectProvider;
import io.ddd4j.boot.core.subject.SubjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebShiroBizConfiguration {

    @Bean
    public SubjectProvider subjectProvider() {
        return new ShiroSubjectProvider();
    }

}
