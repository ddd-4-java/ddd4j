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

/** \u9a8c\u8bc1 <code>SecurityExceptionHandler</code> \u5728<code>authenticationException</code>\u3001<code>lockedException</code>\u3001<code>accessDenied</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class SecurityExceptionHandlerTest {

    /** \u5f53\u524d\u7528\u4f8b\u5b9e\u9645\u8c03\u7528\u6216\u914d\u7f6e\u884c\u4e3a\u7684\u5904\u7406\u5668\u3002 */
    private final SecurityExceptionHandler handler = new SecurityExceptionHandler();

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664\u4e0a\u4e0b\u6587<code>SecurityContextHolder</code>\u3002 */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** \u9a8c\u8bc1<code>response.getStatusCode()</code> \u7b49\u4e8e <code>HttpStatus.UNAUTHORIZED</code>\uff0c<code>response.getBody().getCode()</code> \u7b49\u4e8e <code>Integer.valueOf(401)</code>\u3002 */
    @Test
    void authenticationExceptionReturns401() {
        ResponseEntity<R<String>> response =
                handler.authenticationException(new BadCredentialsException("bad credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Integer.valueOf(401), response.getBody().getCode());
    }

    /** \u9a8c\u8bc1<code>response.getStatusCode()</code> \u7b49\u4e8e <code>HttpStatus.FORBIDDEN</code>\uff0c<code>response.getBody().getCode()</code> \u7b49\u4e8e <code>Integer.valueOf(403)</code>\u3002 */
    @Test
    void lockedExceptionReturns403() {
        ResponseEntity<R<String>> response =
                handler.lockedException(new org.springframework.security.authentication.LockedException("locked"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Integer.valueOf(403), response.getBody().getCode());
    }

    /** \u9a8c\u8bc1<code>response.getStatusCode()</code> \u7b49\u4e8e <code>HttpStatus.UNAUTHORIZED</code>\u3002 */
    @Test
    void accessDeniedWithoutAuthenticationReturns401() {
        ResponseEntity<R<String>> response =
                handler.accessDeniedException(new AccessDeniedException("denied"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    /** \u9a8c\u8bc1<code>response.getStatusCode()</code> \u7b49\u4e8e <code>HttpStatus.FORBIDDEN</code>\uff0c<code>response.getBody().getCode()</code> \u7b49\u4e8e <code>Integer.valueOf(403)</code>\u3002 */
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
