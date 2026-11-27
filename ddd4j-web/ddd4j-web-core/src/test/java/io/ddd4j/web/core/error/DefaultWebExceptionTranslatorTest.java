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

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.exception.*;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>DefaultWebExceptionTranslator</code> \u5728<code>translateBizRuntime</code>\u3001<code>translate</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class DefaultWebExceptionTranslatorTest {

    /** \u5f53\u6846\u67b6\u629b\u51fa\u5f02\u5e38\u65f6\u751f\u6210 HTTP \u9519\u8bef\u7684\u8f6c\u6362\u5668\u3002 */
    private final DefaultWebExceptionTranslator translator = new DefaultWebExceptionTranslator();

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>ApiCode.UNAUTHORIZED.getReason()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void authenticationErrorsUseUnauthorizedStatusAndReason() {
        assertAuthError(new TokenExpiredException("令牌已过期"), ApiCode.UNAUTHORIZED, "令牌已过期");
        assertAuthError(new AuthenticationException(null), ApiCode.UNAUTHORIZED, ApiCode.UNAUTHORIZED.getReason());
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>ApiCode.FORBIDDEN.getReason()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void authorizationErrorsUseForbiddenStatusAndReason() {
        assertAuthError(new PermissionDeniedException("无权查看订单"), ApiCode.FORBIDDEN, "无权查看订单");
        assertAuthError(new AuthorizationException("  "), ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * \u6838\u5bf9<code>error.status()</code> \u7b49\u4e8e <code>expected.getCode().intValue()</code>\uff0c<code>error.code()</code> \u7b49\u4e8e <code>expected.getCode()</code>\u3002
     * @param failure \u89e6\u53d1\u5f53\u524d\u6d4b\u8bd5\u9519\u8bef\u5904\u7406\u8def\u5f84\u7684\u5931\u8d25
     * @param expected \u5f53\u524d\u7528\u4f8b\u8bbe\u5b9a\u7684\u671f\u671b\u503c
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     */
    private void assertAuthError(Throwable failure, ApiCode expected, String message) {
        WebError error = translator.translate(failure);
        assertEquals(expected.getCode().intValue(), error.status());
        assertEquals(expected.getCode(), error.code());
        assertEquals(message, error.message());
        assertNull(error.data());
    }

    /** \u9a8c\u8bc1<code>error.status()</code> \u7b49\u4e8e <code>401</code>\uff0c<code>error.message()</code> \u7b49\u4e8e <code>"unauthorized"</code>\u3002 */
    @Test
    void translateWebStatusException() {
        WebStatusException exception = new WebStatusException(401, "UNAUTHORIZED", "unauthorized",
                "token missing");
        WebError error = translator.translate(exception);
        assertEquals(401, error.status());
        assertEquals("unauthorized", error.message());
    }

    /** \u9a8c\u8bc1<code>error.status()</code> \u7b49\u4e8e <code>409</code>\u3002 */
    @Test
    void translateIdempotentExceptionAsConflict() {
        WebError error = translator.translate(new IdempotentException("duplicate"));
        assertEquals(409, error.status());
    }

    /** \u9a8c\u8bc1<code>translator.translate(new ParamException("bad param")).status()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>translator.translate(new ValidateException("invalid")).status()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>translator.translate(new IllegalArgumentException("bad")).status()</code> \u7b49\u4e8e <code>400</code>\u3002 */
    @Test
    void translateParamAndValidationErrorsAsBadRequest() {
        assertEquals(400, translator.translate(new ParamException("bad param")).status());
        assertEquals(400, translator.translate(new ValidateException("invalid")).status());
        assertEquals(400, translator.translate(new IllegalArgumentException("bad")).status());
    }

    /** \u9a8c\u8bc1<code>translator.translate(new IllegalStateException("state")).status()</code> \u7b49\u4e8e <code>409</code>\u3002 */
    @Test
    void translateIllegalStateAsConflict() {
        assertEquals(409, translator.translate(new IllegalStateException("state")).status());
    }

    /** \u9a8c\u8bc1<code>translator.translate(new NoSuchElementException("missing")).status()</code> \u7b49\u4e8e <code>404</code>\u3002 */
    @Test
    void translateNoSuchElementAsNotFound() {
        assertEquals(404, translator.translate(new NoSuchElementException("missing")).status());
    }

    /** \u9a8c\u8bc1<code>translator.translate(new SecurityException("denied")).status()</code> \u7b49\u4e8e <code>403</code>\u3002 */
    @Test
    void translateSecurityAsForbidden() {
        assertEquals(403, translator.translate(new SecurityException("denied")).status());
    }

    /** \u9a8c\u8bc1<code>error.status()</code> \u7b49\u4e8e <code>422</code>\u3002 */
    @Test
    void translateBizRuntimeWithStatusCode() {
        WebError error = translator.translate(new BizRuntimeException(422, "VALIDATION", "invalid"));
        assertEquals(422, error.status());
    }

    /** \u9a8c\u8bc1<code>error.status()</code> \u7b49\u4e8e <code>500</code>\u3002 */
    @Test
    void translateBizRuntimeNormalizesOutOfRangeCode() {
        WebError error = translator.translate(new BizRuntimeException(99, "X", "msg"));
        assertEquals(500, error.status());
    }

    /** \u9a8c\u8bc1<code>error.status()</code> \u7b49\u4e8e <code>500</code>\uff0c<code>error.message()</code> \u7b49\u4e8e <code>"boom"</code>\u3002 */
    @Test
    void translateGenericExceptionAsInternalError() {
        WebError error = translator.translate(new RuntimeException("boom"));
        assertEquals(500, error.status());
        assertEquals("boom", error.message());
    }

    /** \u9a8c\u8bc1<code>error.status()</code> \u7b49\u4e8e <code>500</code>\uff0c<code>error.message()</code> \u7b49\u4e8e <code>"Internal Server Error"</code>\uff0c<code>error.data()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void translateBlankMessageUsesFallback() {
        WebError error = translator.translate(new RuntimeException());
        assertEquals(500, error.status());
        assertEquals("Internal Server Error", error.message());
        assertNull(error.data());
    }

    /** \u9a8c\u8bc1<code>translator.translate(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void translateRejectsNull() {
        assertThrows(NullPointerException.class, () -> translator.translate(null));
    }
}
