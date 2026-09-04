package io.ddd4j.extension.qlexpress.exception;

/**
 * QLExpress 表达式执行异常。
 */
public class QLExpressExecutionException extends RuntimeException {

    private final String expression;

    public QLExpressExecutionException(String expression, Throwable cause) {
        super("QLExpress 表达式执行失败: " + expression, cause);
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }
}
