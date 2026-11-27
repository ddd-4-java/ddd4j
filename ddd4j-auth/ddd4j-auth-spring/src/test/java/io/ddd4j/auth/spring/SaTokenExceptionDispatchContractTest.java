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

/**
 * 验证 <code>SaTokenExceptionDispatchContract</code> 在 <code>springDispatchesAuthenticationFailure</code>、<code>springDispatchesAuthorizationFailure</code> 等场景中的行为。
 */
class SaTokenExceptionDispatchContractTest {

    /**
     * 当前用例实际调用或配置行为的处理器。
     */
    private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
    /**
     * 当前用例用来转换访问策略或协议字段的解析器。
     */
    private final ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(handler.getClass());

    /**
     * 在测试框架中执行 <code>ApiCode.UNAUTHORIZED.getCode()</code>、<code>ApiCode.UNAUTHORIZED.getReason()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void springDispatchesAuthenticationFailureWithExplicitOrDefaultMessage() throws Exception {
        assertResponse(new TokenExpiredException("请重新登录"), HttpStatus.UNAUTHORIZED,
                ApiCode.UNAUTHORIZED.getCode(), "请重新登录");
        assertResponse(new AuthenticationException(" \t"), HttpStatus.UNAUTHORIZED,
                ApiCode.UNAUTHORIZED.getCode(), ApiCode.UNAUTHORIZED.getReason());
    }

    /**
     * 在测试框架中执行 <code>ApiCode.FORBIDDEN.getCode()</code>、<code>ApiCode.FORBIDDEN.getReason()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void springDispatchesAuthorizationFailureWithExplicitOrDefaultMessage() throws Exception {
        assertResponse(new PermissionDeniedException("没有导出权限"), HttpStatus.FORBIDDEN,
                ApiCode.FORBIDDEN.getCode(), "没有导出权限");
        assertResponse(new AuthorizationException(null), HttpStatus.FORBIDDEN,
                ApiCode.FORBIDDEN.getCode(), ApiCode.FORBIDDEN.getReason());
    }

    /**
     * 验证 Spring 处理原生 Sa-Token 异常时保留 SDK 错误码和消息。
     *
     * @throws Exception 准备测试数据库、启动框架或执行业务合同失败
     */
    @Test
    void springDispatchPreservesSaTokenSdkCodeAndMessage() throws Exception {
        assertResponse(new SaTokenException(11051, "原始 SDK 鉴权错误"), HttpStatus.UNAUTHORIZED,
                11051, "原始 SDK 鉴权错误");
        assertResponse(new SaTokenException(11021, " "), HttpStatus.UNAUTHORIZED, 11021, " ");
    }

    /**
     * 验证<code>resolver.resolveMethodByThrowable(new IllegalStateException("unrelated"))</code>为空。
     */
    @Test
    void springLeavesUnrelatedFailuresUnmapped() {
        assertNull(resolver.resolveMethodByThrowable(new IllegalStateException("unrelated")));
    }

    /**
     * 核对<code>method</code>非空，<code>response.getStatusCode().value()</code> 等于 <code>status.value()</code>。
     *
     * @param failure 触发当前测试错误处理路径的失败
     * @param status  当前 HTTP 或业务流程的状态
     * @param code    用于定位目标测试记录的业务编码
     * @param message 测试传递或用于诊断的消息
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
