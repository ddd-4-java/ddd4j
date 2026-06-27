/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.auth.satoken;

import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import io.ddd4j.auth.satoken.handler.SaMixCheckLoginHandler;
import io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider;
import io.ddd4j.core.subject.SubjectProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;


/**
 *
 */
@Configuration
public class SaTokenEnhanceAutoConfiguration implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        // 重写Sa-Token的注解处理器，增加注解合并功能
        SaAnnotationStrategy.instance.getAnnotation = AnnotatedElementUtils::getMergedAnnotation;
    }

    @Bean
    public SaMixCheckLoginHandler saMixCheckLoginHandler() {
        return new SaMixCheckLoginHandler();
    }

    @Bean
    public SubjectProvider subjectProvider() {
        return new SaTokenSubjectProvider();
    }

}
