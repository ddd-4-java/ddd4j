package io.ddd4j.boot.cmpt.cola.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.alibaba.cola.exception.BizException;
import com.alibaba.cola.exception.SysException;

import io.ddd4j.boot.core.ApiCode;
import io.ddd4j.boot.core.ApiRestResponse;
import io.ddd4j.boot.core.exception.BaseExceptionHandler;

/**
 * COLA 异常处理器。
 *
 * <p>将 COLA 的 {@link BizException}（业务异常）和 {@link SysException}（系统异常）
 * 转换为统一的 {@link ApiRestResponse}。
 *
 * <p>异常映射规则：
 * <ul>
 *   <li>{@link BizException} → {@code ApiCode.SC_FAIL}（业务失败，HTTP 200 但 success=false）</li>
 *   <li>{@link SysException} → {@code ApiCode.SC_INTERNAL_SERVER_ERROR}（500 系统错误）</li>
 *   <li>其他 COLA 异常 → {@code ApiCode.SC_INTERNAL_SERVER_ERROR}</li>
 * </ul>
 *
 * @author wandl
 * @since 3.4.x
 */
@RestControllerAdvice
@Order(0)
public class ColaExceptionHandler extends BaseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ColaExceptionHandler.class);

    /**
     * 处理 COLA 业务异常。
     *
     * @param ex COLA 业务异常
     * @return 统一响应（success=false，包含 errCode/errMsg）
     */
    @ExceptionHandler(BizException.class)
    public ApiRestResponse<String> handleBizException(BizException ex) {
        log.warn("COLA 业务异常: errCode={}, message={}", ex.getErrCode(), ex.getMessage());
        // 将 COLA errCode 拼入 message（ApiRestResponse 的 error 字段类型为 List<Map>，不适合放 errCode）
        String message = ex.getMessage() != null ? ex.getMessage() : "COLA 业务异常";
        return ApiRestResponse.fail(message);
    }

    /**
     * 处理 COLA 系统异常。
     *
     * @param ex COLA 系统异常
     * @return 统一响应（500）
     */
    @ExceptionHandler(SysException.class)
    public ApiRestResponse<String> handleSysException(SysException ex) {
        log.error("COLA 系统异常: errCode={}, message={}", ex.getErrCode(), ex.getMessage(), ex);
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(
                ex.getMessage() != null ? "COLA: " + ex.getMessage() : "COLA: 系统异常");
    }

}
