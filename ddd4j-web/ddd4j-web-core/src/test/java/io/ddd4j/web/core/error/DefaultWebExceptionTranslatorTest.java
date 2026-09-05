/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.core.error;

import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.exception.IdempotentException;
import io.ddd4j.core.exception.ParamException;
import io.ddd4j.core.exception.ValidateException;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultWebExceptionTranslatorTest {

    private final DefaultWebExceptionTranslator translator = new DefaultWebExceptionTranslator();

    @Test
    void translateWebStatusException() {
        WebStatusException exception = new WebStatusException(401, "UNAUTHORIZED", "unauthorized",
                "token missing");
        WebError error = translator.translate(exception);
        assertEquals(401, error.status());
        assertEquals("unauthorized", error.message());
    }

    @Test
    void translateIdempotentExceptionAsConflict() {
        WebError error = translator.translate(new IdempotentException("duplicate"));
        assertEquals(409, error.status());
    }

    @Test
    void translateParamAndValidationErrorsAsBadRequest() {
        assertEquals(400, translator.translate(new ParamException("bad param")).status());
        assertEquals(400, translator.translate(new ValidateException("invalid")).status());
        assertEquals(400, translator.translate(new IllegalArgumentException("bad")).status());
    }

    @Test
    void translateIllegalStateAsConflict() {
        assertEquals(409, translator.translate(new IllegalStateException("state")).status());
    }

    @Test
    void translateNoSuchElementAsNotFound() {
        assertEquals(404, translator.translate(new NoSuchElementException("missing")).status());
    }

    @Test
    void translateSecurityAsForbidden() {
        assertEquals(403, translator.translate(new SecurityException("denied")).status());
    }

    @Test
    void translateBizRuntimeWithStatusCode() {
        WebError error = translator.translate(new BizRuntimeException(422, "VALIDATION", "invalid"));
        assertEquals(422, error.status());
    }

    @Test
    void translateBizRuntimeNormalizesOutOfRangeCode() {
        WebError error = translator.translate(new BizRuntimeException(99, "X", "msg"));
        assertEquals(500, error.status());
    }

    @Test
    void translateGenericExceptionAsInternalError() {
        WebError error = translator.translate(new RuntimeException("boom"));
        assertEquals(500, error.status());
        assertEquals("boom", error.message());
    }

    @Test
    void translateBlankMessageUsesFallback() {
        WebError error = translator.translate(new RuntimeException());
        assertEquals(500, error.status());
        assertEquals("Internal Server Error", error.message());
        assertNull(error.data());
    }

    @Test
    void translateRejectsNull() {
        assertThrows(NullPointerException.class, () -> translator.translate(null));
    }
}
