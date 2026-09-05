package io.ddd4j.extension.qlexpress.model;

/**
 * 表达式语法校验结果。
 */
public final class QLExpressValidationResult {

    private final boolean valid;
    private final String message;

    private QLExpressValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public static QLExpressValidationResult success() {
        return new QLExpressValidationResult(true, "表达式语法正确");
    }

    public static QLExpressValidationResult invalid(String message) {
        return new QLExpressValidationResult(false, message);
    }

    public boolean valid() { return valid; }
    public String message() { return message; }
}
