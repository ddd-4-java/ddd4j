package io.ddd4j.extension.express.domain.event;

import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import io.ddd4j.extension.express.domain.model.entity.RuleId;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 规则删除事件
 * 
 * <p>领域事件：当规则被删除时发布此事件。
 * 用于通知其他模块规则已删除，可以触发缓存清理、日志记录等操作。
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public class RuleDeletedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final RuleId ruleId;
    private final String ruleCode;
    private final String ruleName;
    private final String ruleType;
    private final LocalDateTime occurredAt;

    /**
     * 构造函数
     * 
     * @param rule 规则定义，不能为null
     */
    public RuleDeletedEvent(RuleDefinition rule) {
        this.ruleId = rule.getRuleId();
        this.ruleCode = rule.getRuleCode();
        this.ruleName = rule.getRuleName();
        this.ruleType = rule.getRuleType();
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 构造函数
     * 
     * @param ruleId 规则ID，不能为null
     * @param ruleCode 规则编码，不能为null
     * @param ruleName 规则名称
     * @param ruleType 规则类型
     */
    public RuleDeletedEvent(RuleId ruleId, String ruleCode, String ruleName, String ruleType) {
        this.ruleId = ruleId;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.ruleType = ruleType;
        this.occurredAt = LocalDateTime.now();
    }

    public RuleId getRuleId() {
        return ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getRuleType() {
        return ruleType;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return "RuleDeletedEvent{" +
                "ruleId=" + ruleId +
                ", ruleCode='" + ruleCode + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", ruleType='" + ruleType + '\'' +
                ", occurredAt=" + occurredAt +
                '}';
    }
}

