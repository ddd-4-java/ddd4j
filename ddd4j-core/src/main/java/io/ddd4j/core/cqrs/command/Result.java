package io.ddd4j.core.cqrs.command;

import java.util.Optional;

/** 命令执行结果，与 HTTP 响应对象分离。 */
public final class Result<T> {
    public static final int SUCCESS = 0;
    public static final int FAILURE = 1;
    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    public static Result<Void> ok() { return new Result<Void>(SUCCESS, "ok", null); }
    public static <T> Result<T> ok(T data) { return new Result<T>(SUCCESS, "ok", data); }
    public static <T> Result<T> fail(String message) { return new Result<T>(FAILURE, message, null); }
    public static <T> Result<T> fail(int code, String message) { return new Result<T>(code, message, null); }
    public boolean isSuccess() { return code == SUCCESS; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public Optional<T> data() { return Optional.ofNullable(data); }
}
