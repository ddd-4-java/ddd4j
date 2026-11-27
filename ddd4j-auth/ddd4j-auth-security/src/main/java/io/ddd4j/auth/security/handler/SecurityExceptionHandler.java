package io.ddd4j.auth.security.handler;

import io.ddd4j.core.ApiRestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Objects;

/**
 * Spring Security 异常处理器（Spring Web 专属）。
 *
 * <p>本类放在 ddd4j-auth-security 模块（允许 Spring 依赖）。
 * 将 Spring Security 鉴权异常统一映射为 ddd4j {@link ApiRestResponse}，
 * 与 SaTokenExceptionHandler / ShiroExceptionHandler 互补，保证三鉴权异常响应格式一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ControllerAdvice
@ResponseBody
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j(topic = "### DDD4J-AUTH : SecurityExceptionHandler ###")
public class SecurityExceptionHandler {

    /**
     * 401\uff1a\u672a\u8ba4\u8bc1\uff08\u51ed\u8bc1\u9519\u8bef / \u8d26\u53f7\u8fc7\u671f\uff09
     *
     * @param ex \u8ba4\u8bc1\u5f02\u5e38
     * @return \u672a\u8ba4\u8bc1\u54cd\u5e94
     */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class,
            AccountExpiredException.class})
    public ResponseEntity<ApiRestResponse<String>> authenticationException(Exception ex) {
        log.warn("Spring Security 认证异常：{}", ex.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(401, "未登录或登录已过期"), HttpStatus.UNAUTHORIZED);
    }

    /**
     * 403\uff1a\u8d26\u53f7\u9501\u5b9a / \u7981\u7528
     *
     * @param ex \u8d26\u53f7\u72b6\u6001\u5f02\u5e38
     * @return \u7981\u6b62\u8bbf\u95ee\u54cd\u5e94
     */
    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<ApiRestResponse<String>> lockedException(Exception ex) {
        log.warn("Spring Security 账号状态异常：{}", ex.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(403, "账号已被锁定或禁用"), HttpStatus.FORBIDDEN);
    }

    /**
     * 403\uff1a\u65e0\u6743\u9650\u8bbf\u95ee
     *
     * @param ex \u6388\u6743\u5f02\u5e38
     * @return \u672a\u8ba4\u8bc1\u6216\u7981\u6b62\u8bbf\u95ee\u54cd\u5e94
     */
    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<ApiRestResponse<String>> accessDeniedException(AccessDeniedException ex) {
        log.warn("Spring Security 授权异常：{}", ex.getMessage());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean unauthenticated = Objects.isNull(authentication)
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
        HttpStatus status = unauthenticated ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        return new ResponseEntity<>(ApiRestResponse.of(status.value(), "无权限访问"), status);
    }

}
