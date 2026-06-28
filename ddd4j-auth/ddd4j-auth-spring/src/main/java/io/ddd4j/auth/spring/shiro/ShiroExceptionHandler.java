package io.ddd4j.auth.spring.shiro;

import io.ddd4j.core.ApiRestResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Shiro 异常处理器（Spring Web 专属）。
 *
 * <p>将 Shiro 的鉴权异常统一映射为 ddd4j {@link ApiRestResponse}，保证三鉴权异常响应格式一致。
 * 与 {@code SaTokenExceptionHandler} 互补，覆盖 Shiro 兼容场景。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
@ControllerAdvice
@ResponseBody
@Slf4j(topic = "### DDD4J-AUTH : ShiroExceptionHandler ###")
@ConditionalOnClass(name = "org.apache.shiro.ShiroException")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ShiroExceptionHandler {

    /** 401：未认证（未登录 / Token 失效） */
    @ExceptionHandler({UnknownAccountException.class, IncorrectCredentialsException.class,
            AuthenticationException.class})
    public ResponseEntity<ApiRestResponse<String>> authenticationException(Exception ex) {
        log.warn("Shiro 认证异常：{}", ex.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(401, "未登录或登录已过期"), HttpStatus.UNAUTHORIZED);
    }

    /** 403：账号锁定 */
    @ExceptionHandler({LockedAccountException.class})
    public ResponseEntity<ApiRestResponse<String>> lockedAccountException(LockedAccountException ex) {
        log.warn("Shiro 账号锁定：{}", ex.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(403, "账号已被锁定"), HttpStatus.FORBIDDEN);
    }

    /** 403：无权限 / 无角色 */
    @ExceptionHandler({UnauthorizedException.class, AuthorizationException.class})
    public ResponseEntity<ApiRestResponse<String>> authorizationException(Exception ex) {
        log.warn("Shiro 授权异常：{}", ex.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(403, "无权限访问"), HttpStatus.FORBIDDEN);
    }

}
