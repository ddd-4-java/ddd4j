package io.ddd4j.extension.express.domain.model.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则验证结果值对象
 *
 * <p>领域模型：不可变的值对象，表示规则语法验证的结果。
 * 使用Builder模式构建，确保不可变性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
public class RuleValidationResult {

    /**
     * 验证是否通过
     */
    private final boolean valid;

    /**
     * 验证消息
     */
    private final String message;

    /**
     * 错误列表，验证失败时的详细错误信息
     */
    private final List<String> errors;

    private RuleValidationResult(Builder builder) {
        this.valid = builder.valid;
        this.message = builder.message;
        this.errors = java.util.Objects.nonNull(builder.errors) ? new ArrayList<>(builder.errors) : new ArrayList<>();
    }

    /**
     * 创建Builder实例
     *
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public static class Builder {
        private boolean valid;
        private String message;
        private List<String> errors;

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder errors(List<String> errors) {
            this.errors = errors;
            return this;
        }

        public Builder addError(String error) {
            if (java.util.Objects.isNull(this.errors)) {
                this.errors = new ArrayList<>();
            }
            this.errors.add(error);
            return this;
        }

        public RuleValidationResult build() {
            return new RuleValidationResult(this);
        }
    }
}

