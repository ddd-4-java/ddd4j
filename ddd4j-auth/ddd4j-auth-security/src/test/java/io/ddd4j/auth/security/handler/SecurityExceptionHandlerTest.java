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
package io.ddd4j.auth.security.handler;

import io.ddd4j.core.api.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 <code>SecurityExceptionHandler</code> 在<code>authenticationException</code>、<code>lockedException</code>、<code>accessDenied</code>等操作中的行为契约。
 */
class SecurityExceptionHandlerTest {

    /**
     * 当前用例实际调用或配置行为的处理器。
     */
    private final SecurityExceptionHandler handler = new SecurityExceptionHandler();

    /**
     * 测试后依次清除上下文<code>SecurityContextHolder</code>。
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 验证<code>response.getStatusCode()</code> 等于 <code>HttpStatus.UNAUTHORIZED</code>，<code>response.getBody().getCode()</code> 等于 <code>Integer.valueOf(401)</code>。
     */
    @Test
    void authenticationExceptionReturns401() {
        ResponseEntity<R<String>> response =
                handler.authenticationException(new BadCredentialsException("bad credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Integer.valueOf(401), response.getBody().getCode());
    }

    /**
     * 验证<code>response.getStatusCode()</code> 等于 <code>HttpStatus.FORBIDDEN</code>，<code>response.getBody().getCode()</code> 等于 <code>Integer.valueOf(403)</code>。
     */
    @Test
    void lockedExceptionReturns403() {
        ResponseEntity<R<String>> response =
                handler.lockedException(new org.springframework.security.authentication.LockedException("locked"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Integer.valueOf(403), response.getBody().getCode());
    }

    /**
     * 验证<code>response.getStatusCode()</code> 等于 <code>HttpStatus.UNAUTHORIZED</code>。
     */
    @Test
    void accessDeniedWithoutAuthenticationReturns401() {
        ResponseEntity<R<String>> response =
                handler.accessDeniedException(new AccessDeniedException("denied"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    /**
     * 验证<code>response.getStatusCode()</code> 等于 <code>HttpStatus.FORBIDDEN</code>，<code>response.getBody().getCode()</code> 等于 <code>Integer.valueOf(403)</code>。
     */
    @Test
    void accessDeniedWithAuthenticatedUserReturns403() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", "pw",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"))));

        ResponseEntity<R<String>> response =
                handler.accessDeniedException(new AccessDeniedException("denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Integer.valueOf(403), response.getBody().getCode());
    }
}
