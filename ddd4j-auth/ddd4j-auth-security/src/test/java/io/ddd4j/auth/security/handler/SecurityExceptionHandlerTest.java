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

import io.ddd4j.core.ApiRestResponse;
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

class SecurityExceptionHandlerTest {

    private final SecurityExceptionHandler handler = new SecurityExceptionHandler();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticationExceptionReturns401() {
        ResponseEntity<ApiRestResponse<String>> response =
                handler.authenticationException(new BadCredentialsException("bad credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Integer.valueOf(401), response.getBody().getCode());
    }

    @Test
    void lockedExceptionReturns403() {
        ResponseEntity<ApiRestResponse<String>> response =
                handler.lockedException(new org.springframework.security.authentication.LockedException("locked"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Integer.valueOf(403), response.getBody().getCode());
    }

    @Test
    void accessDeniedWithoutAuthenticationReturns401() {
        ResponseEntity<ApiRestResponse<String>> response =
                handler.accessDeniedException(new AccessDeniedException("denied"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void accessDeniedWithAuthenticatedUserReturns403() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", "pw",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"))));

        ResponseEntity<ApiRestResponse<String>> response =
                handler.accessDeniedException(new AccessDeniedException("denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Integer.valueOf(403), response.getBody().getCode());
    }
}
