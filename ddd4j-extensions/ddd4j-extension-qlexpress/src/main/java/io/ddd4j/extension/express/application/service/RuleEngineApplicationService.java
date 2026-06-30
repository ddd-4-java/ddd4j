package io.ddd4j.extension.express.application.service;

import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import io.ddd4j.extension.express.domain.model.vo.RuleExecutionResult;
import io.ddd4j.extension.express.domain.model.vo.RuleValidationResult;
import io.ddd4j.extension.express.domain.repository.RuleDefinitionRepository;
import io.ddd4j.extension.express.domain.service.RuleEngineDomainService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 规则引擎应用服务
 * 负责用例编排、事务管理、缓存协调等
 *
 * <p>应用层服务，负责协调领域服务和基础设施层，提供规则执行、验证等用例编排功能。
 * 所有规则操作都会自动处理缓存，确保数据一致性。
 *
 * <p>注意：此类通过ExpressAutoConfiguration自动配置，无需手动添加@Service注解
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
public class RuleEngineApplicationService {

    private final RuleEngineDomainService ruleEngineDomainService;
    private final RuleDefinitionRepository ruleRepository;
    private final RuleCacheService ruleCacheService;

    public RuleEngineApplicationService(RuleEngineDomainService ruleEngineDomainService,
                                        RuleDefinitionRepository ruleRepository,
                                        RuleCacheService ruleCacheService) {
        this.ruleEngineDomainService = ruleEngineDomainService;
        this.ruleRepository = ruleRepository;
        this.ruleCacheService = ruleCacheService;
    }

    /**
     * 执行规则
     *
     * <p>根据规则编码执行规则表达式，自动处理缓存查询和规则可用性检查。
     * 执行过程包括：
     * <ol>
     *   <li>从缓存或数据库获取规则定义</li>
     *   <li>检查规则是否启用</li>
     *   <li>执行规则表达式</li>
     *   <li>记录执行时间</li>
     * </ol>
     *
     * @param ruleCode 规则编码，唯一标识一个规则
     * @param context  执行上下文，包含规则表达式中使用的变量
     * @return 规则执行结果，包含执行状态、结果值、错误信息等
     */
    public RuleExecutionResult executeRule(String ruleCode, Map<String, Object> context) {
        // 1. 获取规则定义（带缓存）
        RuleDefinition rule = getRuleDefinition(ruleCode);

        if (java.util.Objects.isNull(rule)) {
            return RuleExecutionResult.builder()
                    .success(false)
                    .errorCode("RULE_NOT_FOUND")
                    .errorMessage("规则不存在: " + ruleCode)
                    .build();
        }

        // 2. 检查规则是否可用
        if (!ruleEngineDomainService.isRuleAvailable(rule)) {
            return RuleExecutionResult.builder()
                    .success(false)
                    .errorCode("RULE_DISABLED")
                    .errorMessage("规则已禁用: " + ruleCode)
                    .build();
        }

        // 3. 执行规则
        long startTime = System.currentTimeMillis();
        RuleExecutionResult result = ruleEngineDomainService.executeRule(rule, context);
        long executionTime = System.currentTimeMillis() - startTime;

        // 4. 设置执行时间
        return RuleExecutionResult.builder()
                .success(result.isSuccess())
                .errorCode(result.getErrorCode())
                .errorMessage(result.getErrorMessage())
                .result(result.getResult())
                .ruleCode(ruleCode)
                .executedAt(result.getExecutedAt())
                .executionTime(executionTime)
                .build();
    }

    /**
     * 批量执行规则
     *
     * <p>按顺序执行多个规则，每个规则独立执行，互不影响。
     *
     * @param ruleCodes 规则编码列表，不能为null
     * @param context   执行上下文，所有规则共享同一个上下文
     * @return 规则执行结果列表，顺序与输入的规则编码列表一致
     */
    public List<RuleExecutionResult> executeRules(List<String> ruleCodes, Map<String, Object> context) {
        return ruleCodes.stream()
                .map(ruleCode -> executeRule(ruleCode, context))
                .collect(Collectors.toList());
    }

    /**
     * 验证规则语法
     *
     * <p>检查规则表达式是否符合QLExpress语法规范，不执行规则。
     * 用于规则创建或更新前的语法校验。
     *
     * @param expression 规则表达式，QLExpress语法
     * @return 验证结果，包含是否有效和错误信息
     */
    public RuleValidationResult validateRule(String expression) {
        return ruleEngineDomainService.validateExpression(expression);
    }

    /**
     * 获取规则定义（带缓存）
     *
     * <p>优先从缓存获取规则，缓存未命中时从数据库查询并更新缓存。
     *
     * @param ruleCode 规则编码
     * @return 规则定义，如果不存在返回null
     */
    private RuleDefinition getRuleDefinition(String ruleCode) {
        // 先从缓存获取
        RuleDefinition cachedRule = ruleCacheService.get(ruleCode);
        if (java.util.Objects.nonNull(cachedRule)) {
            return cachedRule;
        }

        // 缓存未命中，从数据库获取
        return ruleRepository.findByRuleCode(ruleCode)
                .map(rule -> {
                    // 存入缓存
                    ruleCacheService.put(ruleCode, rule);
                    return rule;
                })
                .orElse(null);
    }

    /**
     * 清除规则缓存
     *
     * @param ruleCode 规则编码，不能为null
     */
    public void clearRuleCache(String ruleCode) {
        ruleCacheService.evict(ruleCode);
    }

    /**
     * 清除所有规则缓存
     *
     * <p>清除Redis中所有规则相关的缓存数据，谨慎使用。
     */
    public void clearAllRuleCache() {
        ruleCacheService.evictAll();
    }
}

