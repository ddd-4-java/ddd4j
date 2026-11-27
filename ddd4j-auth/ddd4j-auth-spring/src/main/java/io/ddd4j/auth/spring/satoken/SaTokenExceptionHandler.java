package io.ddd4j.auth.spring.satoken;

import cn.dev33.satoken.exception.SaTokenException;
import io.ddd4j.core.api.R;
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

    /**
     * \u5c06 Sa-Token \u9274\u6743\u5f02\u5e38\u6620\u5c04\u4e3a\u672a\u8ba4\u8bc1\u54cd\u5e94\u3002
     * @param exception Sa-Token \u5f02\u5e38
     * @return \u672a\u8ba4\u8bc1\u54cd\u5e94
     */
    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<R<String>> accessDeniedException(SaTokenException exception) {
        log.warn("Sa-Token 鉴权异常：code={}, msg={}", exception.getCode(), exception.getMessage());
        return new ResponseEntity<>(R.fail(exception.getCode(), exception.getMessage()),
                HttpStatus.UNAUTHORIZED);
    }
}
