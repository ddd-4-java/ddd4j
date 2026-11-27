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

    /** \u9a8c\u8bc1<code>context.getBean(GlobalExceptionHandler.class).getMessageSource()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>context.getBean(NestedMessageSource.class)</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.REQUEST_TOO_LONG.getCode()</code>\uff0c<code>response.getMsg().contains("\u5355\u4e2a\u6587\u4ef6\u8d85\u8fc7\u5141\u8bb8\u7684\u6700\u5927\u9650\u5236")</code>\u4e3a\u771f\u3002 */
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
