package io.ddd4j.boot.cmpt.dubbo.exception;

import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.ddd4j.boot.core.ApiCode;
import io.ddd4j.boot.core.ApiRestResponse;
import io.ddd4j.boot.core.exception.BaseExceptionHandler;

/**
 * Dubbo RPC 异常处理器。
 *
 * <p>将 {@link RpcException} 及其子类转换为统一的 {@link ApiRestResponse}，
 * 避免 Dubbo 的框架异常直接暴露给客户端。
 *
 * <p>异常映射规则：
 * <ul>
 *   <li>{@code TIMEOUT_EXCEPTION} / {@code LIMIT_EXCEEDED} → 504 Gateway Timeout</li>
 *   <li>{@code NETWORK_EXCEPTION} / {@code FORBIDDEN_EXCEPTION} → 503 Service Unavailable</li>
 *   <li>{@code METHOD_NOT_FOUND} / {@code SERVICE_NOT_FOUND} → 404 Not Found</li>
 *   <li>{@code BIZ_EXCEPTION} → 500 Internal Server Error（业务异常）</li>
 *   <li>其他 → 500 Internal Server Error</li>
 * </ul>
 *
 * @author wandl
 * @since 3.4.x
 */
@RestControllerAdvice
@Order(0)
public class DubboExceptionHandler extends BaseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DubboExceptionHandler.class);

    /**
     * 处理 Dubbo RPC 异常。
     *
     * @param ex RPC 异常
     * @return 统一响应
     */
    @ExceptionHandler(RpcException.class)
    public ApiRestResponse<String> handleRpcException(RpcException ex) {
        log.error("Dubbo RPC 异常: code={}, message={}", ex.getCode(), ex.getMessage(), ex);

        ApiCode apiCode = mapRpcExceptionToApiCode(ex);
        String message = getRpcMessage(ex);
        return apiCode.toResponse(message);
    }

    /**
     * 将 Dubbo RpcException 的 code 映射到 HTTP 状态码。
     *
     * @param ex RPC 异常
     * @return 对应的 ApiCode
     */
    private ApiCode mapRpcExceptionToApiCode(RpcException ex) {
        if (ex.isTimeout()) {
            return ApiCode.SC_GATEWAY_TIMEOUT;
        }
        if (ex.isBiz()) {
            return ApiCode.SC_INTERNAL_SERVER_ERROR;
        }
        switch (ex.getCode()) {
            case RpcException.FORBIDDEN_EXCEPTION:
                return ApiCode.SC_SERVICE_UNAVAILABLE;
            case RpcException.NETWORK_EXCEPTION:
                return ApiCode.SC_SERVICE_UNAVAILABLE;
            case RpcException.METHOD_NOT_FOUND:
                return ApiCode.SC_NOT_FOUND;
            case RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER:
                return ApiCode.SC_NOT_FOUND;
            case RpcException.LIMIT_EXCEEDED_EXCEPTION:
                return ApiCode.SC_GATEWAY_TIMEOUT;
            case RpcException.TIMEOUT_TERMINATE:
                return ApiCode.SC_GATEWAY_TIMEOUT;
            case RpcException.SERIALIZATION_EXCEPTION:
                return ApiCode.SC_BAD_REQUEST;
            case RpcException.VALIDATION_EXCEPTION:
                return ApiCode.SC_BAD_REQUEST;
            default:
                return ApiCode.SC_INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * 提取友好的异常消息。
     *
     * @param ex RPC 异常
     * @return 消息字符串
     */
    private String getRpcMessage(RpcException ex) {
        // RpcException 的 message 可能包含堆栈信息，只取第一行
        String message = ex.getMessage();
        if (message != null) {
            int newline = message.indexOf('\n');
            if (newline > 0) {
                message = message.substring(0, newline);
            }
        }
        return message != null ? "Dubbo: " + message : "Dubbo: RPC 调用异常";
    }

}
