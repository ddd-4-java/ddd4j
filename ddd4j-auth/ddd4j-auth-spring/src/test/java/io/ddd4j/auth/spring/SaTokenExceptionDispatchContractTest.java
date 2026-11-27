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

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>SaTokenExceptionDispatchContract</code> \u5728 <code>springDispatchesAuthenticationFailure</code>\u3001<code>springDispatchesAuthorizationFailure</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class SaTokenExceptionDispatchContractTest {

    /** \u5f53\u524d\u7528\u4f8b\u5b9e\u9645\u8c03\u7528\u6216\u914d\u7f6e\u884c\u4e3a\u7684\u5904\u7406\u5668\u3002 */
    private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
    /** \u5f53\u524d\u7528\u4f8b\u7528\u6765\u8f6c\u6362\u8bbf\u95ee\u7b56\u7565\u6216\u534f\u8bae\u5b57\u6bb5\u7684\u89e3\u6790\u5668\u3002 */
    private final ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(handler.getClass());

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>ApiCode.UNAUTHORIZED.getCode()</code>\u3001<code>ApiCode.UNAUTHORIZED.getReason()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void springDispatchesAuthenticationFailureWithExplicitOrDefaultMessage() throws Exception {
        assertResponse(new TokenExpiredException("请重新登录"), HttpStatus.UNAUTHORIZED,
                ApiCode.UNAUTHORIZED.getCode(), "请重新登录");
        assertResponse(new AuthenticationException(" \t"), HttpStatus.UNAUTHORIZED,
                ApiCode.UNAUTHORIZED.getCode(), ApiCode.UNAUTHORIZED.getReason());
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>ApiCode.FORBIDDEN.getCode()</code>\u3001<code>ApiCode.FORBIDDEN.getReason()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void springDispatchesAuthorizationFailureWithExplicitOrDefaultMessage() throws Exception {
        assertResponse(new PermissionDeniedException("没有导出权限"), HttpStatus.FORBIDDEN,
                ApiCode.FORBIDDEN.getCode(), "没有导出权限");
        assertResponse(new AuthorizationException(null), HttpStatus.FORBIDDEN,
                ApiCode.FORBIDDEN.getCode(), ApiCode.FORBIDDEN.getReason());
    }

    /**
     * \u9a8c\u8bc1 Spring \u5904\u7406\u539f\u751f Sa-Token \u5f02\u5e38\u65f6\u4fdd\u7559 SDK \u9519\u8bef\u7801\u548c\u6d88\u606f\u3002
     * @throws Exception \u51c6\u5907\u6d4b\u8bd5\u6570\u636e\u5e93\u3001\u542f\u52a8\u6846\u67b6\u6216\u6267\u884c\u4e1a\u52a1\u5408\u540c\u5931\u8d25
     */
    @Test
    void springDispatchPreservesSaTokenSdkCodeAndMessage() throws Exception {
        assertResponse(new SaTokenException(11051, "原始 SDK 鉴权错误"), HttpStatus.UNAUTHORIZED,
                11051, "原始 SDK 鉴权错误");
        assertResponse(new SaTokenException(11021, " "), HttpStatus.UNAUTHORIZED, 11021, " ");
    }

    /** \u9a8c\u8bc1<code>resolver.resolveMethodByThrowable(new IllegalStateException("unrelated"))</code>\u4e3a\u7a7a\u3002 */
    @Test
    void springLeavesUnrelatedFailuresUnmapped() {
        assertNull(resolver.resolveMethodByThrowable(new IllegalStateException("unrelated")));
    }

    /**
     * \u6838\u5bf9<code>method</code>\u975e\u7a7a\uff0c<code>response.getStatusCode().value()</code> \u7b49\u4e8e <code>status.value()</code>\u3002
     * @param failure \u89e6\u53d1\u5f53\u524d\u6d4b\u8bd5\u9519\u8bef\u5904\u7406\u8def\u5f84\u7684\u5931\u8d25
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param code \u7528\u4e8e\u5b9a\u4f4d\u76ee\u6807\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e1a\u52a1\u7f16\u7801
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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
