/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.web.webmvc.error;

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.R;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.validation.ConstraintDeclarationException;
import javax.validation.ConstraintDefinitionException;
import javax.validation.ConstraintViolationException;
import javax.validation.GroupDefinitionException;
import javax.validation.UnexpectedTypeException;
import javax.validation.ValidationException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 不同 Bean Validation API 下相同的异常响应契约。 */
class ValidationExceptionParityTest {

    /** \u5f53\u524d\u7528\u4f8b\u5b9e\u9645\u8c03\u7528\u6216\u914d\u7f6e\u884c\u4e3a\u7684\u5904\u7406\u5668\u3002 */
    private GlobalExceptionHandler handler;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>properties.setEnabled()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        ServerI18nProperties properties = new ServerI18nProperties();
        properties.setEnabled(false);
        ReflectionTestUtils.setField(handler, "serverI18NProperties", properties);
    }

    /** \u9a8c\u8bc1<code>validation.getCode()</code> \u7b49\u4e8e <code>ApiCode.BAD_REQUEST.getCode()</code>\uff0c<code>validation.getMsg().contains("\u53c2\u6570\u6821\u9a8c\u5f02\u5e38")</code>\u4e3a\u771f\uff0c<code>violation.getMsg()</code> \u7b49\u4e8e <code>"invalid constraint"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>declaration.getCode()</code> \u7b49\u4e8e <code>ApiCode.SERVER_ERROR.getCode()</code>\uff0c<code>definition.getCode()</code> \u7b49\u4e8e <code>ApiCode.SERVER_ERROR.getCode()</code>\uff0c<code>type.getMsg().contains("\u7ea6\u675f\u9a8c\u8bc1\u5668")</code>\u4e3a\u771f\u3002 */
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
