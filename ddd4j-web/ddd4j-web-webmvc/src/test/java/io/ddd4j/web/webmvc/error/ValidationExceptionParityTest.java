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

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        ServerI18nProperties properties = new ServerI18nProperties();
        properties.setEnabled(false);
        ReflectionTestUtils.setField(handler, "serverI18NProperties", properties);
    }

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
