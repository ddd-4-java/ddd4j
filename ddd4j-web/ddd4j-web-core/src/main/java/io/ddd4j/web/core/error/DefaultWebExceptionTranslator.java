package io.ddd4j.web.core.error;

import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.exception.IdempotentException;
import io.ddd4j.core.exception.ParamException;
import io.ddd4j.core.exception.ValidateException;
import io.ddd4j.kit.lang.StrKit;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * ddd4j 默认异常翻译策略。
 */
public final class DefaultWebExceptionTranslator implements WebExceptionTranslator {

    /** \u8868\u793a\u8bf7\u6c42\u53c2\u6570\u6216\u683c\u5f0f\u975e\u6cd5\u7684\u72b6\u6001\u7801\u3002 */
    public static final int BAD_REQUEST = 400;
    /** \u8868\u793a\u7f3a\u5c11\u6216\u65e0\u6548\u6388\u6743\u51ed\u8bc1\u7684\u72b6\u6001\u7801\u3002 */
    public static final int UNAUTHORIZED = 401;
    /** \u8868\u793a\u5df2\u8ba4\u8bc1\u4f46\u65e0\u6743\u8bbf\u95ee\u7684\u72b6\u6001\u7801\u3002 */
    public static final int FORBIDDEN = 403;
    /** \u8868\u793a\u76ee\u6807\u8d44\u6e90\u4e0d\u5b58\u5728\u7684\u72b6\u6001\u7801\u3002 */
    public static final int NOT_FOUND = 404;
    /** \u8868\u793a\u8bf7\u6c42\u4e0e\u5f53\u524d\u8d44\u6e90\u72b6\u6001\u51b2\u7a81\uff08\u5982\u91cd\u590d\u63d0\u4ea4\uff09\u7684\u72b6\u6001\u7801\u3002 */
    public static final int CONFLICT = 409;
    /** \u8868\u793a\u670d\u52a1\u7aef\u53d1\u751f\u672a\u9884\u671f\u9519\u8bef\u7684\u72b6\u6001\u7801\u3002 */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /**
     * \u5c06\u5df2\u77e5\u4e1a\u52a1\u5f02\u5e38\u8f6c\u6362\u4e3a\u7a33\u5b9a\u7684 HTTP \u9519\u8bef\u54cd\u5e94\u3002
     * @param throwable \u5f85\u7ffb\u8bd1\u7684\u5f02\u5e38
     * @return \u5305\u542b\u72b6\u6001\u7801\u3001\u9519\u8bef\u7801\u548c\u6d88\u606f\u7684\u54cd\u5e94
     */
    @Override
    public WebError translate(Throwable throwable) {
        Throwable cause = Objects.requireNonNull(throwable, "throwable must not be null");
        if (cause instanceof WebStatusException exception) {
            return new WebError(exception.getStatus(), exception.getCode(), message(exception), exception.getData());
        }
        if (cause instanceof IdempotentException) {
            return error(CONFLICT, cause);
        }
        if (cause instanceof ParamException || cause instanceof ValidateException
                || cause instanceof IllegalArgumentException) {
            return error(BAD_REQUEST, cause);
        }
        if (cause instanceof IllegalStateException) {
            return error(CONFLICT, cause);
        }
        if (cause instanceof NoSuchElementException) {
            return error(NOT_FOUND, cause);
        }
        if (cause instanceof SecurityException) {
            return error(FORBIDDEN, cause);
        }
        if (cause instanceof BizRuntimeException exception) {
            int status = normalizeStatus(exception.getCode());
            return new WebError(status, Objects.nonNull(exception.getCode()) ? exception.getCode() : status,
                    message(exception), null);
        }
        return error(INTERNAL_SERVER_ERROR, cause);
    }

    /**
     * \u5c06 HTTP \u72b6\u6001\u548c\u539f\u5f02\u5e38\u8f6c\u6362\u4e3a\u5171\u7528 Web \u9519\u8bef\u3002
     * @param status \u51c6\u5907\u8fd4\u56de\u7ed9 HTTP \u5ba2\u6237\u7aef\u7684\u72b6\u6001\u7801
     * @param throwable \u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u5e26\u72b6\u6001\u53ca\u5b89\u5168\u54cd\u5e94\u5185\u5bb9\u7684 Web \u9519\u8bef
     */
    private WebError error(int status, Throwable throwable) {
        return new WebError(status, status, message(throwable), null);
    }

    /**
     * \u4ece\u5f02\u5e38\u4e2d\u8bfb\u53d6\u54cd\u5e94\u53ef\u4f7f\u7528\u7684\u6d88\u606f\u6587\u672c\u3002
     * @param throwable \u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u7528\u4e8e\u5f53\u524d\u5f02\u5e38\u8f6c\u6362\u7684\u6d88\u606f
     */
    private String message(Throwable throwable) {
        return StrKit.isBlank(throwable.getMessage()) ? "Internal Server Error" : throwable.getMessage();
    }

    /**
     * \u4fdd\u7559\u5408\u6cd5\u7684 HTTP \u9519\u8bef\u72b6\u6001\uff0c\u5176\u4ed6\u503c\u5f52\u4e3a\u5185\u90e8\u670d\u52a1\u5668\u9519\u8bef\u3002
     * @param code \u5f53\u524d\u8981\u8f6c\u6362\u7684\u4e1a\u52a1\u9519\u8bef\u7801\u6216 HTTP \u72b6\u6001\u7801
     * @return \u8f6c\u6362\u540e\u7684 HTTP \u9519\u8bef\u72b6\u6001\u7801
     */
    private int normalizeStatus(Integer code) {
        if (Objects.nonNull(code) && code >= BAD_REQUEST && code <= 599) {
            return code;
        }
        return INTERNAL_SERVER_ERROR;
    }
}
