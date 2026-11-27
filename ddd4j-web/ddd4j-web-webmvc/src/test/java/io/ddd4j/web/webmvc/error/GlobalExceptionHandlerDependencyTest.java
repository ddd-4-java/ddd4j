/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.web.webmvc.error;

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.R;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.extension.web.multipart.MaxUploadSizePerFileExceededException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线 Spring 扩展依赖装配与上传异常响应契约。
 */
class GlobalExceptionHandlerDependencyTest {

    /**
     * 验证<code>context.getBean(GlobalExceptionHandler.class).getMessageSource()</code> 与…为同一实例 <code>context.getBean(NestedMessageSource.class)</code>。
     */
    @Test
    void injectsConfiguredNestedMessageSource() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NestedMessageSource.class, () -> new NestedMessageSource());
            context.registerBean(ServerI18nProperties.class);
            context.registerBean(GlobalExceptionHandler.class);
            context.refresh();
            assertSame(context.getBean(NestedMessageSource.class),
                    context.getBean(GlobalExceptionHandler.class).getMessageSource());
        }
    }

    /**
     * 验证<code>response.getCode()</code> 等于 <code>ApiCode.REQUEST_TOO_LONG.getCode()</code>，<code>response.getMsg().contains("单个文件超过允许的最大限制")</code>为真。
     */
    @Test
    void reportsPerFileUploadLimit() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NestedMessageSource.class, () -> new NestedMessageSource());
            context.registerBean(ServerI18nProperties.class);
            context.registerBean(GlobalExceptionHandler.class);
            context.refresh();
            R<String> response = context.getBean(GlobalExceptionHandler.class)
                    .maxUploadSizePerFileExceededException(new MaxUploadSizePerFileExceededException(2048L));
            assertEquals(ApiCode.REQUEST_TOO_LONG.getCode(), response.getCode());
            assertTrue(response.getMsg().contains("单个文件超过允许的最大限制"));
        }
    }
}
