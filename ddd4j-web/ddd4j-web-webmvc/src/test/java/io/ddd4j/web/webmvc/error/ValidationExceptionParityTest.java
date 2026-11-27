/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.web.webmvc.error;

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.R;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import jakarta.validation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.extension.context.NestedMessageSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不同 Bean Validation API 下相同的异常响应契约。
 */
class ValidationExceptionParityTest {

    /**
     * 当前用例实际调用或配置行为的处理器。
     */
    private GlobalExceptionHandler handler;

    /**
     * 测试执行前执行 <code>properties.setEnabled()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        ServerI18nProperties properties = new ServerI18nProperties();
        properties.setEnabled(false);
        handler = new GlobalExceptionHandler(new NestedMessageSource(), properties);
    }

    /**
     * 验证<code>validation.getCode()</code> 等于 <code>ApiCode.BAD_REQUEST.getCode()</code>，<code>validation.getMsg().contains("参数校验异常")</code>为真，<code>violation.getMsg()</code> 等于 <code>"invalid constraint"</code>。
     */
    @Test
    void validationErrorsReturnBadRequest() {
        R<String> validation = handler.validationException(new ValidationException("invalid"));
        assertEquals(ApiCode.BAD_REQUEST.getCode(), validation.getCode());
        assertTrue(validation.getMsg().contains("参数校验异常"));

        R<?> violation = handler.constraintViolationException(
                new ConstraintViolationException("invalid constraint", Collections.emptySet()));
        assertEquals(ApiCode.METHOD_ARGUMENT_NOT_VALID.getCode(), violation.getCode());
        assertEquals("invalid constraint", violation.getMsg());
    }

    /**
     * 验证<code>declaration.getCode()</code> 等于 <code>ApiCode.SERVER_ERROR.getCode()</code>，<code>definition.getCode()</code> 等于 <code>ApiCode.SERVER_ERROR.getCode()</code>，<code>type.getMsg().contains("约束验证器")</code>为真。
     */
    @Test
    void invalidConstraintDefinitionsReturnServerError() {
        R<String> declaration = handler.constraintDeclarationException(
                new ConstraintDeclarationException("invalid declaration"));
        R<String> definition = handler.constraintDefinitionException(
                new ConstraintDefinitionException("invalid definition"));
        R<String> group = handler.groupDefinitionException(new GroupDefinitionException("invalid group"));
        R<String> type = handler.unexpectedTypeException(new UnexpectedTypeException("invalid type"));
        assertEquals(ApiCode.SERVER_ERROR.getCode(), declaration.getCode());
        assertEquals(ApiCode.SERVER_ERROR.getCode(), definition.getCode());
        assertEquals(ApiCode.SERVER_ERROR.getCode(), group.getCode());
        assertEquals(ApiCode.SERVER_ERROR.getCode(), type.getCode());
        assertTrue(declaration.getMsg().contains("约束声明"));
        assertTrue(definition.getMsg().contains("约束定义"));
        assertTrue(group.getMsg().contains("约束组定义"));
        assertTrue(type.getMsg().contains("约束验证器"));
    }
}
