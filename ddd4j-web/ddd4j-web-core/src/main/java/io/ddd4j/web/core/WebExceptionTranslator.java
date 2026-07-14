package io.ddd4j.web.core;

/**
 * 将运行时异常转换为稳定的 HTTP 与响应体语义。
 */
@FunctionalInterface
public interface WebExceptionTranslator {

    WebError translate(Throwable throwable);
}
