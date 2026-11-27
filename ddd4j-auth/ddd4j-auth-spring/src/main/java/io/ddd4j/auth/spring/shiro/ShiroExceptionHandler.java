package io.ddd4j.auth.spring.shiro;

import io.ddd4j.core.api.R;
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

    /**
     * \u5c06 Shiro \u8ba4\u8bc1\u5f02\u5e38\u6620\u5c04\u4e3a\u672a\u8ba4\u8bc1\u54cd\u5e94\u3002
     * @param exception Shiro \u8ba4\u8bc1\u5f02\u5e38
     * @return \u672a\u8ba4\u8bc1\u54cd\u5e94
     */
    @ExceptionHandler({UnknownAccountException.class, IncorrectCredentialsException.class,
            AuthenticationException.class})
    public ResponseEntity<R<String>> authenticationException(Exception exception) {
        log.warn("Shiro 认证异常：{}", exception.getMessage());
        return new ResponseEntity<>(R.fail(401, "未登录或登录已过期"), HttpStatus.UNAUTHORIZED);
    }

    /**
     * \u5c06\u8d26\u53f7\u9501\u5b9a\u5f02\u5e38\u6620\u5c04\u4e3a\u7981\u6b62\u8bbf\u95ee\u54cd\u5e94\u3002
     * @param exception \u8d26\u53f7\u9501\u5b9a\u5f02\u5e38
     * @return \u7981\u6b62\u8bbf\u95ee\u54cd\u5e94
     */
    @ExceptionHandler(LockedAccountException.class)
    public ResponseEntity<R<String>> lockedAccountException(LockedAccountException exception) {
        log.warn("Shiro 账号锁定：{}", exception.getMessage());
        return new ResponseEntity<>(R.fail(403, "账号已被锁定"), HttpStatus.FORBIDDEN);
    }

    /**
     * \u5c06 Shiro \u6388\u6743\u5f02\u5e38\u6620\u5c04\u4e3a\u7981\u6b62\u8bbf\u95ee\u54cd\u5e94\u3002
     * @param exception Shiro \u6388\u6743\u5f02\u5e38
     * @return \u7981\u6b62\u8bbf\u95ee\u54cd\u5e94
     */
    @ExceptionHandler({UnauthorizedException.class, AuthorizationException.class})
    public ResponseEntity<R<String>> authorizationException(Exception exception) {
        log.warn("Shiro 授权异常：{}", exception.getMessage());
        return new ResponseEntity<>(R.fail(403, "无权限访问"), HttpStatus.FORBIDDEN);
    }
}
