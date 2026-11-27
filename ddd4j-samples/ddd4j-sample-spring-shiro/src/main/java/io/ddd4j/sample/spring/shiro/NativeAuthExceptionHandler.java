package io.ddd4j.sample.spring.shiro;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 将原生安全失败优先转换为 HTTP 状态，避免被通用 Web 异常处理器映射为 500。 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NativeAuthExceptionHandler {
    /**
     * 执行 <code>authentication</code>，调用 <code>ResponseEntity.status</code>。
     *
     * @param failure \u539f\u751f\u8ba4\u8bc1\u5f02\u5e38
     * @return \u672a\u8ba4\u8bc1\u54cd\u5e94
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> authentication(AuthenticationException failure) {
        return ResponseEntity.status(401).body("authentication required");
    }
    /**
     * 执行 <code>authorization</code>，调用 <code>ResponseEntity.status</code>。
     *
     * @param failure \u539f\u751f\u6388\u6743\u5f02\u5e38
     * @return \u7981\u6b62\u8bbf\u95ee\u54cd\u5e94
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<String> authorization(AuthorizationException failure) {
        return ResponseEntity.status(403).body("permission denied");
    }
    /**
     * 执行 <code>status</code>，调用 <code>ResponseEntity.status</code>、<code>failure.getStatusCode</code>。
     *
     * @param failure \u5165\u53e3\u660e\u786e\u7684\u72b6\u6001\u5f02\u5e38
     * @return \u4fdd\u7559 HTTP \u72b6\u6001\u7684\u54cd\u5e94
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> status(ResponseStatusException failure) {
        return ResponseEntity.status(failure.getStatusCode()).body("request rejected");
    }
}
