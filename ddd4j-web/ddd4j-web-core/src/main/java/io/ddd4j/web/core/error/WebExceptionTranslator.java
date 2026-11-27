package io.ddd4j.web.core.error;

/**
 * 将运行时异常转换为稳定的 HTTP 与响应体语义。
 */
@FunctionalInterface
public interface WebExceptionTranslator {

    /**
     * \u5c06\u5f02\u5e38\u8f6c\u6362\u4e3a\u6846\u67b6\u65e0\u5173\u7684 Web \u9519\u8bef\u3002
     * @param throwable \u539f\u59cb\u5f02\u5e38
     * @return Web \u9519\u8bef
     */
    WebError translate(Throwable throwable);
}
