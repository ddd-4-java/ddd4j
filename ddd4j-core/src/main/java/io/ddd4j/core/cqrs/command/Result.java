package io.ddd4j.core.cqrs.command;

import lombok.Getter;

import java.util.Optional;

/**
 * CQRS 命令执行结果。
 * <p>
 * 与 HTTP 响应 {@code io.ddd4j.core.api.R} 区分：
 * <ul>
 *   <li>{@link Result}：命令执行结果（领域层）</li>
 *   <li>{@code R}：HTTP 响应包装（接口层）</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Getter
public final class Result<T> {

    /**
     * 成功状态码。
     */
    public static final int SUCCESS = 0;

    /**
     * 失败状态码。
     */
    public static final int FAILURE = 1;

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功结果（无数据）。
     */
    public static Result<Void> ok() {
        return new Result<>(SUCCESS, "ok", null);
    }

    /**
     * 成功结果（带数据）。
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS, "ok", data);
    }

    /**
     * 失败结果。
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(FAILURE, message, null);
    }

    /**
     * 失败结果（带错误码）。
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public boolean isSuccess() {
        return code == SUCCESS;
    }

    public Optional<T> data() {
        return Optional.ofNullable(data);
    }
}