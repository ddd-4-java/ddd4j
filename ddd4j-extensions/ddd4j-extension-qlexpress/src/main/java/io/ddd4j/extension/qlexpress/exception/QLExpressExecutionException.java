package io.ddd4j.extension.qlexpress.exception;

/**
 * QLExpress 表达式执行异常。
 */
public class QLExpressExecutionException extends RuntimeException {

    /** \u6267\u884c\u5931\u8d25\u7684\u8868\u8fbe\u5f0f\u3002 */
    private final String expression;

    /**
     * \u521b\u5efa\u5305\u542b\u5931\u8d25\u8868\u8fbe\u5f0f\u7684\u6267\u884c\u5f02\u5e38\u3002
     * @param expression \u5931\u8d25\u8868\u8fbe\u5f0f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public QLExpressExecutionException(String expression, Throwable cause) {
        super("QLExpress 表达式执行失败: " + expression, cause);
        this.expression = expression;
    }

    /**
     * \u83b7\u53d6\u5931\u8d25\u8868\u8fbe\u5f0f\u3002
     * @return \u5931\u8d25\u8868\u8fbe\u5f0f
     */
    public String getExpression() {
        return expression;
    }
}
