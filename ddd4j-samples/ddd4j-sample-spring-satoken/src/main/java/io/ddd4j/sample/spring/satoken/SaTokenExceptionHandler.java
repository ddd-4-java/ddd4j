package io.ddd4j.sample.spring.satoken;

import cn.dev33.satoken.exception.SaTokenException;
import io.ddd4j.core.ApiRestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Sa-Token 异常处理器（Spring Web 专属，从 ddd4j-auth-satoken 迁入）。
 *
 * <p>本类依赖 spring-web 的 @ControllerAdvice，因此放在 ddd4j-auth-spring 桥接模块。
 * ddd4j-auth-satoken 保持纯 Java，零 Spring 依赖。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ControllerAdvice
@ResponseBody
@Slf4j(topic = "### DDD4J-AUTH : SaTokenExceptionHandler ###")
public class SaTokenExceptionHandler {

    /**
     * 401 (Unauthorized)：Sa-Token 鉴权异常统一处理。
     */
    @ExceptionHandler({SaTokenException.class})
    public ResponseEntity<ApiRestResponse<String>> accessDeniedException(SaTokenException ex) {
        log.warn("Sa-Token 鉴权异常：code={}, msg={}", ex.getCode(), ex.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(ex.getCode(), ex.getMessage()), HttpStatus.UNAUTHORIZED);
    }

}
