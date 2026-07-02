package io.ddd4j.extension.express.application.dto;

import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 规则DTO映射器
 *
 * <p>应用层：负责领域实体和DTO之间的转换。
 * 遵循DDD规范，接口层不应该直接使用领域实体。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Component
public class RuleMapper {

    /**
     * 将领域实体转换为响应DTO
     *
     * @param rule 规则定义实体
     * @return 规则响应DTO
     */
    public RuleResponse toResponse(RuleDefinition rule) {
        if (Objects.isNull(rule)) {
            return null;
        }

        return RuleResponse.builder()
                .id(rule.getId())
                .ruleCode(rule.getRuleCode())
                .ruleName(rule.getRuleName())
                .ruleExpression(rule.getRuleExpression())
                .ruleDescription(rule.getRuleDescription())
                .ruleType(rule.getRuleType())
                .enabled(rule.getEnabled())
                .priority(rule.getPriority())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .functionClass(rule.getFunctionClass())
                .functionMethod(rule.getFunctionMethod())
                .functionScript(rule.getFunctionScript())
                .functionType(rule.getFunctionType())
                .returnType(rule.getReturnType())
                .parameterTypes(rule.getParameterTypes())
                .build();
    }

    /**
     * 将创建请求DTO转换为领域实体
     *
     * @param request 创建规则请求DTO
     * @return 规则定义实体
     */
    public RuleDefinition toEntity(CreateRuleRequest request) {
        if (Objects.isNull(request)) {
            return null;
        }

        RuleDefinition rule = new RuleDefinition();
        rule.setRuleCode(request.getRuleCode());
        rule.setRuleName(request.getRuleName());
        rule.setRuleExpression(request.getRuleExpression());
        rule.setRuleDescription(request.getRuleDescription());
        rule.setRuleType(request.getRuleType());
        rule.setEnabled(Objects.nonNull(request.getEnabled()) ? request.getEnabled() : true);
        rule.setPriority(Objects.nonNull(request.getPriority()) ? request.getPriority() : 0);

        // 函数相关字段
        rule.setFunctionClass(request.getFunctionClass());
        rule.setFunctionMethod(request.getFunctionMethod());
        rule.setFunctionScript(request.getFunctionScript());
        rule.setFunctionType(request.getFunctionType());
        rule.setReturnType(request.getReturnType());
        rule.setParameterTypes(request.getParameterTypes());

        return rule;
    }

    /**
     * 将更新请求DTO更新到领域实体
     *
     * @param rule    现有的规则定义实体
     * @param request 更新规则请求DTO
     */
    public void updateEntity(RuleDefinition rule, UpdateRuleRequest request) {
        if (Objects.isNull(rule) || Objects.isNull(request)) {
            return;
        }

        rule.setRuleName(request.getRuleName());
        rule.setRuleExpression(request.getRuleExpression());
        rule.setRuleDescription(request.getRuleDescription());
        rule.setRuleType(request.getRuleType());

        if (Objects.nonNull(request.getEnabled())) {
            rule.setEnabled(request.getEnabled());
        }
        if (Objects.nonNull(request.getPriority())) {
            rule.setPriority(request.getPriority());
        }

        // 函数相关字段
        rule.setFunctionClass(request.getFunctionClass());
        rule.setFunctionMethod(request.getFunctionMethod());
        rule.setFunctionScript(request.getFunctionScript());
        rule.setFunctionType(request.getFunctionType());
        rule.setReturnType(request.getReturnType());
        rule.setParameterTypes(request.getParameterTypes());
    }
}

