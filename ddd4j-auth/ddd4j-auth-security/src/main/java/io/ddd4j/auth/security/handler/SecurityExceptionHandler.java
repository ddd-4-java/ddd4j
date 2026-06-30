package io.ddd4j.auth.security.handler;

import io.ddd4j.core.ApiRestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Spring Security 异常处理器（Spring Web 专属）。
 *
 * <p>本类放在 ddd4j-auth-security 模块（允许 Spring 依赖）。
 * 将 Spring Security 鉴权异常统一映射为 ddd4j {@link ApiRestResponse}，
 * 与 SaTokenExceptionHandler / ShiroExceptionHandler 互补，保证三鉴权异常响应格式一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
@ControllerAdvice
@ResponseBody
@Slf4j(topic = "### DDD4J-AUTH : SecurityExceptionHandler ###")
public class SecurityExceptionHandler {

    /**
     * 401：未认证（凭证错误 / 账号过期）
     */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class,
            AccountExpiredException.class})
    public ResponseEntity<ApiRestResponse<String>> authenticationException(Exception ex) {
        log.warn("Spring Security 认证异常：{}", ex.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(401, "未登录或登录已过期"), HttpStatus.UNAUTHORIZED);
    }

    /**
     * 403：账号锁定 / 禁用
     */
    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<ApiRestResponse<String>> lockedException(Exception ex) {
        log.warn("Spring Security 账号状态异常：{}", ex.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(403, "账号已被锁定或禁用"), HttpStatus.FORBIDDEN);
    }

    /**
     * 403：无权限访问
     */
    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<ApiRestResponse<String>> accessDeniedException(AccessDeniedException ex) {
        log.warn("Spring Security 授权异常：{}", ex.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(403, "无权限访问"), HttpStatus.FORBIDDEN);
    }

}
