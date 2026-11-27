package io.ddd4j.extension.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.Objects;

/**
 * SQL 执行的 OTel Span 辅助工具。
 *
 * <p>为 {@code Ddd4jSqlObservationSink} 提供 span 包装，
 * 无 OTel 时为 noop。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * SqlSpan.execute("mybatis", "SELECT * FROM orders WHERE id = ?", () -> {
 *     return mapper.selectById(id);
 * });
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class SqlSpan {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>SqlSpan</code> \u5b9e\u4f8b\u3002 */
    private SqlSpan() {
    }

    /**
     * 包装 SQL 执行。
     *
     * @param dbSystem  数据库系统（mybatis、jpa、jdbc 等）
     * @param statement SQL 语句或描述
     * @param runnable  SQL 操作
     */
    public static void execute(String dbSystem, String statement, Runnable runnable) {
        if (!Ddd4jOtel.isAvailable()) {
            runnable.run();
            return;
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.sql.execute")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(Ddd4jOtel.ATTR_DB_SYSTEM, dbSystem)
                .startSpan();
        if (Objects.nonNull(statement) && statement.length() < 256) {
            span.setAttribute(Ddd4jOtel.ATTR_DB_STATEMENT, statement);
        } else if (Objects.nonNull(statement)) {
            span.setAttribute(Ddd4jOtel.ATTR_DB_STATEMENT, statement.substring(0, 256) + "...");
        }
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            runnable.run();
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * \u5305\u88c5\u5e26\u8fd4\u56de\u503c\u7684 SQL \u6267\u884c\u3002
     *
     * @param <T> \u8fd4\u56de\u503c\u7c7b\u578b
     * @param dbSystem \u6570\u636e\u5e93\u7cfb\u7edf
     * @param statement SQL \u8bed\u53e5
     * @param supplier \u5f85\u8ffd\u8e2a\u64cd\u4f5c
     * @return \u64cd\u4f5c\u7ed3\u679c
     */
    public static <T> T execute(String dbSystem, String statement, java.util.function.Supplier<T> supplier) {
        if (!Ddd4jOtel.isAvailable()) {
            return supplier.get();
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.sql.execute")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(Ddd4jOtel.ATTR_DB_SYSTEM, dbSystem)
                .startSpan();
        if (Objects.nonNull(statement) && statement.length() < 256) {
            span.setAttribute(Ddd4jOtel.ATTR_DB_STATEMENT, statement);
        }
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            return supplier.get();
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            throw t;
        } finally {
            span.end();
        }
    }
}
