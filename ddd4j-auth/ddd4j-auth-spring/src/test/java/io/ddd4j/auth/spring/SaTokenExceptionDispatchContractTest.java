package io.ddd4j.auth.spring;

import cn.dev33.satoken.exception.SaTokenException;
import io.ddd4j.auth.spring.satoken.SaTokenExceptionHandler;
import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.R;
import io.ddd4j.core.exception.AuthenticationException;
import io.ddd4j.core.exception.AuthorizationException;
import io.ddd4j.core.exception.PermissionDeniedException;
import io.ddd4j.core.exception.TokenExpiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.support.InvocableHandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SaTokenExceptionDispatchContractTest {

    private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
    private final ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(handler.getClass());

    @Test
    void springDispatchesAuthenticationFailureWithExplicitOrDefaultMessage() throws Exception {
        assertResponse(new TokenExpiredException("请重新登录"), HttpStatus.UNAUTHORIZED,
                ApiCode.UNAUTHORIZED.getCode(), "请重新登录");
        assertResponse(new AuthenticationException(" \t"), HttpStatus.UNAUTHORIZED,
                ApiCode.UNAUTHORIZED.getCode(), ApiCode.UNAUTHORIZED.getReason());
    }

    @Test
    void springDispatchesAuthorizationFailureWithExplicitOrDefaultMessage() throws Exception {
        assertResponse(new PermissionDeniedException("没有导出权限"), HttpStatus.FORBIDDEN,
                ApiCode.FORBIDDEN.getCode(), "没有导出权限");
        assertResponse(new AuthorizationException(null), HttpStatus.FORBIDDEN,
                ApiCode.FORBIDDEN.getCode(), ApiCode.FORBIDDEN.getReason());
    }

    @Test
    void springDispatchPreservesSaTokenSdkCodeAndMessage() throws Exception {
        assertResponse(new SaTokenException(11051, "原始 SDK 鉴权错误"), HttpStatus.UNAUTHORIZED,
                11051, "原始 SDK 鉴权错误");
        assertResponse(new SaTokenException(11021, " "), HttpStatus.UNAUTHORIZED, 11021, " ");
    }

    @Test
    void springLeavesUnrelatedFailuresUnmapped() {
        assertNull(resolver.resolveMethodByThrowable(new IllegalStateException("unrelated")));
    }

    private void assertResponse(Exception failure, HttpStatus status, Integer code, String message) throws Exception {
        Method method = resolver.resolveMethodByThrowable(failure);
        assertNotNull(method, failure.getClass().getSimpleName());
        InvocableHandlerMethod invocation = new InvocableHandlerMethod(handler, method);
        ResponseEntity<?> response = assertInstanceOf(ResponseEntity.class,
                invocation.invokeForRequest(null, null, failure));
        assertEquals(status.value(), response.getStatusCode().value());
        R<?> body = assertInstanceOf(R.class, response.getBody());
        assertEquals(code, body.getCode());
        assertEquals(message, body.getMsg());
        assertNull(body.getData());
    }
}
