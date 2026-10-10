package io.ddd4j.extension.qlexpress.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 表达式语法校验结果。
 */
public final class QLExpressValidationResult {

    private static final long serialVersionUID = 0L;

    private final boolean valid;

    private final String message;

    public static QLExpressValidationResult success() {
        return new QLExpressValidationResult(true, "表达式语法正确");
    }

    public static QLExpressValidationResult invalid(String message) {
        return new QLExpressValidationResult(false, message);
    }

    @JsonCreator()
    public QLExpressValidationResult(@JsonProperty("valid") boolean valid, @JsonProperty("message") String message) {
        this.valid = valid;
        this.message = message;
    }

    @JsonProperty("valid")
    public boolean valid() {
        return valid;
    }

    @JsonProperty("message")
    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        QLExpressValidationResult other = (QLExpressValidationResult) obj;
        return this.valid == other.valid && Objects.equals(this.message, other.message);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(valid);
        result = 31 * result + Objects.hashCode(message);
        return result;
    }

    @Override
    public String toString() {
        return "QLExpressValidationResult[valid=" + valid + ", message=" + message + "]";
    }
}
