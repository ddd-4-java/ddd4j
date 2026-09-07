package io.ddd4j.auth.spring.satoken;

import cn.dev33.satoken.exception.SaTokenException;
import io.ddd4j.core.ApiRestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Sa-Token 异常的 Spring Web 统一处理器。
 */
@ControllerAdvice
@ResponseBody
@Slf4j(topic = "### DDD4J-AUTH : SaTokenExceptionHandler ###")
public class SaTokenExceptionHandler {

    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<ApiRestResponse<String>> accessDeniedException(SaTokenException exception) {
        log.warn("Sa-Token 鉴权异常：code={}, msg={}", exception.getCode(), exception.getMessage());
        return new ResponseEntity<>(ApiRestResponse.of(exception.getCode(), exception.getMessage()),
                HttpStatus.UNAUTHORIZED);
    }
}
