package io.ddd4j.auth.spring.shiro;

import io.ddd4j.core.ApiRestResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Shiro 异常的 Spring Web 统一处理器。
 */
@ControllerAdvice
@ResponseBody
@Slf4j(topic = "### DDD4J-AUTH : ShiroExceptionHandler ###")
public class ShiroExceptionHandler {

    @ExceptionHandler({UnknownAccountException.class, IncorrectCredentialsException.class,
            AuthenticationException.class})
    public ResponseEntity<ApiRestResponse<String>> authenticationException(Exception exception) {
        log.warn("Shiro 认证异常：{}", exception.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(401, "未登录或登录已过期"), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(LockedAccountException.class)
    public ResponseEntity<ApiRestResponse<String>> lockedAccountException(LockedAccountException exception) {
        log.warn("Shiro 账号锁定：{}", exception.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(403, "账号已被锁定"), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({UnauthorizedException.class, AuthorizationException.class})
    public ResponseEntity<ApiRestResponse<String>> authorizationException(Exception exception) {
        log.warn("Shiro 授权异常：{}", exception.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(403, "无权限访问"), HttpStatus.FORBIDDEN);
    }
}
