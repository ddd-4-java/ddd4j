/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.auth.shiro;

import io.ddd4j.auth.shiro.subject.ShiroSubjectProvider;
import io.ddd4j.core.subject.SubjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebShiroBizConfiguration {

    @Bean
    public SubjectProvider subjectProvider() {
        return new ShiroSubjectProvider();
    }

}
