package io.ddd4j.web.core.error;

import java.io.Serializable;

/**
 * Web 适配层可移植的 HTTP 状态异常。
 */
public class WebStatusException extends RuntimeException {

    /** HTTP \u72b6\u6001\u7801\u3002 */
    private final int status;
    /** \u4e1a\u52a1\u9519\u8bef\u7801\u3002 */
    private final Serializable code;
    /** \u9519\u8bef\u6570\u636e\u3002 */
    private final Object data;

    /**
     * \u521b\u5efa\u5e26 HTTP \u72b6\u6001\u7801\u7684 Web \u5f02\u5e38\u3002
     * @param status HTTP \u72b6\u6001\u7801
     * @param message \u9519\u8bef\u6d88\u606f
     */
    public WebStatusException(int status, String message) {
        this(status, status, message, null);
    }

    /**
     * \u521b\u5efa\u5e26\u4e1a\u52a1\u9519\u8bef\u4fe1\u606f\u7684 Web \u5f02\u5e38\u3002
     * @param status HTTP \u72b6\u6001\u7801
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f
     * @param data \u9519\u8bef\u6570\u636e
     */
    public WebStatusException(int status, Serializable code, String message, Object data) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }

    /**
     * \u8fd4\u56deHTTP \u72b6\u6001\u7801\u3002
     * @return HTTP \u72b6\u6001\u7801
     */
    public int getStatus() {
        return status;
    }

    /**
     * \u8fd4\u56de\u9519\u8bef\u7801\u3002
     * @return \u9519\u8bef\u7801
     */
    public Serializable getCode() {
        return code;
    }

    /**
     * \u8fd4\u56de\u9519\u8bef\u6570\u636e\u3002
     * @return \u9519\u8bef\u6570\u636e
     */
    public Object getData() {
        return data;
    }
}
