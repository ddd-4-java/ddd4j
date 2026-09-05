package io.ddd4j.web.core.error;

import io.ddd4j.core.api.R;

import java.io.Serializable;

/**
 * HTTP 状态与 ddd4j 响应体之间的统一错误表示。
 */public final class WebError {

    private final int status;
    private final Serializable code;
    private final String message;
    private final Object data;

    public WebError(int status, Serializable code, String message, Object data) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public R<Object> toResponse() {
        return R.fail(code, message, data);
    }

    public int status() { return status; }
    public Serializable code() { return code; }
    public String message() { return message; }
    public Object data() { return data; }
}