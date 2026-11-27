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

/**
 * 验证 <code>DefaultWebExceptionTranslator</code> 在<code>translateBizRuntime</code>、<code>translate</code>等操作中的行为契约。
 */
class DefaultWebExceptionTranslatorTest {

    /**
     * 当框架抛出异常时生成 HTTP 错误的转换器。
     */
    private final DefaultWebExceptionTranslator translator = new DefaultWebExceptionTranslator();

    /**
     * 在测试框架中执行 <code>ApiCode.UNAUTHORIZED.getReason()</code>，验证该场景。
     */
    @Test
    void authenticationErrorsUseUnauthorizedStatusAndReason() {
        assertAuthError(new TokenExpiredException("令牌已过期"), ApiCode.UNAUTHORIZED, "令牌已过期");
        assertAuthError(new AuthenticationException(null), ApiCode.UNAUTHORIZED, ApiCode.UNAUTHORIZED.getReason());
    }

    /**
     * 在测试框架中执行 <code>ApiCode.FORBIDDEN.getReason()</code>，验证该场景。
     */
    @Test
    void authorizationErrorsUseForbiddenStatusAndReason() {
        assertAuthError(new PermissionDeniedException("无权查看订单"), ApiCode.FORBIDDEN, "无权查看订单");
        assertAuthError(new AuthorizationException("  "), ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * 核对<code>error.status()</code> 等于 <code>expected.getCode().intValue()</code>，<code>error.code()</code> 等于 <code>expected.getCode()</code>。
     *
     * @param failure  触发当前测试错误处理路径的失败
     * @param expected 当前用例设定的期望值
     * @param message  测试传递或用于诊断的消息
     */
    private void assertAuthError(Throwable failure, ApiCode expected, String message) {
        WebError error = translator.translate(failure);
        assertEquals(expected.getCode().intValue(), error.status());
        assertEquals(expected.getCode(), error.code());
        assertEquals(message, error.message());
        assertNull(error.data());
    }

    /**
     * 验证<code>error.status()</code> 等于 <code>401</code>，<code>error.message()</code> 等于 <code>"unauthorized"</code>。
     */
    @Test
    void translateWebStatusException() {
        WebStatusException exception = new WebStatusException(401, "UNAUTHORIZED", "unauthorized",
                "token missing");
        WebError error = translator.translate(exception);
        assertEquals(401, error.status());
        assertEquals("unauthorized", error.message());
    }

    /**
     * 验证<code>error.status()</code> 等于 <code>409</code>。
     */
    @Test
    void translateIdempotentExceptionAsConflict() {
        WebError error = translator.translate(new IdempotentException("duplicate"));
        assertEquals(409, error.status());
    }

    /**
     * 验证<code>translator.translate(new ParamException("bad param")).status()</code> 等于 <code>400</code>，<code>translator.translate(new ValidateException("invalid")).status()</code> 等于 <code>400</code>，<code>translator.translate(new IllegalArgumentException("bad")).status()</code> 等于 <code>400</code>。
     */
    @Test
    void translateParamAndValidationErrorsAsBadRequest() {
        assertEquals(400, translator.translate(new ParamException("bad param")).status());
        assertEquals(400, translator.translate(new ValidateException("invalid")).status());
        assertEquals(400, translator.translate(new IllegalArgumentException("bad")).status());
    }

    /**
     * 验证<code>translator.translate(new IllegalStateException("state")).status()</code> 等于 <code>409</code>。
     */
    @Test
    void translateIllegalStateAsConflict() {
        assertEquals(409, translator.translate(new IllegalStateException("state")).status());
    }

    /**
     * 验证<code>translator.translate(new NoSuchElementException("missing")).status()</code> 等于 <code>404</code>。
     */
    @Test
    void translateNoSuchElementAsNotFound() {
        assertEquals(404, translator.translate(new NoSuchElementException("missing")).status());
    }

    /**
     * 验证<code>translator.translate(new SecurityException("denied")).status()</code> 等于 <code>403</code>。
     */
    @Test
    void translateSecurityAsForbidden() {
        assertEquals(403, translator.translate(new SecurityException("denied")).status());
    }

    /**
     * 验证<code>error.status()</code> 等于 <code>422</code>。
     */
    @Test
    void translateBizRuntimeWithStatusCode() {
        WebError error = translator.translate(new BizRuntimeException(422, "VALIDATION", "invalid"));
        assertEquals(422, error.status());
    }

    /**
     * 验证<code>error.status()</code> 等于 <code>500</code>。
     */
    @Test
    void translateBizRuntimeNormalizesOutOfRangeCode() {
        WebError error = translator.translate(new BizRuntimeException(99, "X", "msg"));
        assertEquals(500, error.status());
    }

    /**
     * 验证<code>error.status()</code> 等于 <code>500</code>，<code>error.message()</code> 等于 <code>"boom"</code>。
     */
    @Test
    void translateGenericExceptionAsInternalError() {
        WebError error = translator.translate(new RuntimeException("boom"));
        assertEquals(500, error.status());
        assertEquals("boom", error.message());
    }

    /**
     * 验证<code>error.status()</code> 等于 <code>500</code>，<code>error.message()</code> 等于 <code>"Internal Server Error"</code>，<code>error.data()</code>为空。
     */
    @Test
    void translateBlankMessageUsesFallback() {
        WebError error = translator.translate(new RuntimeException());
        assertEquals(500, error.status());
        assertEquals("Internal Server Error", error.message());
        assertNull(error.data());
    }

    /**
     * 验证<code>translator.translate(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void translateRejectsNull() {
        assertThrows(NullPointerException.class, () -> translator.translate(null));
    }
}
