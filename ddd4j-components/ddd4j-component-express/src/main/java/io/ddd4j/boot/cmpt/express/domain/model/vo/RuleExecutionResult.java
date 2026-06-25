package io.ddd4j.boot.cmpt.express.domain.model.vo;

import java.time.LocalDateTime;

/**
 * 规则执行结果值对象
 * 
 * <p>领域模型：不可变的值对象，表示规则执行的结果。
 * 使用Builder模式构建，确保不可变性。
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public class RuleExecutionResult {

    /** 执行是否成功 */
    private final boolean success;
    
    /** 错误代码，执行失败时使用 */
    private final String errorCode;
    
    /** 错误消息，执行失败时使用 */
    private final String errorMessage;
    
    /** 执行结果值，执行成功时返回 */
    private final Object result;
    
    /** 规则编码 */
    private final String ruleCode;
    
    /** 执行时间 */
    private final LocalDateTime executedAt;
    
    /** 执行耗时（毫秒） */
    private final long executionTime;

    private RuleExecutionResult(Builder builder) {
        this.success = builder.success;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
        this.result = builder.result;
        this.ruleCode = builder.ruleCode;
        this.executedAt = builder.executedAt != null ? builder.executedAt : LocalDateTime.now();
        this.executionTime = builder.executionTime;
    }

    /**
     * 创建Builder实例
     * 
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Object getResult() {
        return result;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public static class Builder {
        private boolean success;
        private String errorCode;
        private String errorMessage;
        private Object result;
        private String ruleCode;
        private LocalDateTime executedAt;
        private long executionTime;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        public Builder ruleCode(String ruleCode) {
            this.ruleCode = ruleCode;
            return this;
        }

        public Builder executedAt(LocalDateTime executedAt) {
            this.executedAt = executedAt;
            return this;
        }

        public Builder executionTime(long executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public RuleExecutionResult build() {
            return new RuleExecutionResult(this);
        }
    }
}

