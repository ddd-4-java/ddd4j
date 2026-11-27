package io.ddd4j.sample.spring.satoken;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 将原生安全失败优先转换为 HTTP 状态，避免被通用 Web 异常处理器映射为 500。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NativeAuthExceptionHandler {
    /**
     * @param failure 原生认证异常
     * @return 未认证响应
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<String> authentication(NotLoginException failure) {
        return ResponseEntity.status(401).body("authentication required");
    }

    /**
     * @param failure 原生授权异常
     * @return 禁止访问响应
     */
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<String> authorization(NotPermissionException failure) {
        return ResponseEntity.status(403).body("permission denied");
    }

    /**
     * @param failure 入口明确的状态异常
     * @return 保留 HTTP 状态的响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> status(ResponseStatusException failure) {
        return ResponseEntity.status(failure.getStatusCode()).body("request rejected");
    }
}
