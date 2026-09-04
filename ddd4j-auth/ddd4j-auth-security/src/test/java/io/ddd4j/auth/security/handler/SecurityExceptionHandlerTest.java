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
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"));

        ResponseEntity<ApiRestResponse<String>> response =
                handler.accessDeniedException(new AccessDeniedException("denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Integer.valueOf(403), response.getBody().getCode());
    }
}
