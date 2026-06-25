package io.ddd4j.extension.express.infrastructure.service;

import com.alibaba.qlexpress4.CheckOptions;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.runtime.context.ExpressContext;
import com.alibaba.qlexpress4.runtime.context.MapExpressContext;
import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import io.ddd4j.extension.express.domain.model.vo.RuleExecutionResult;
import io.ddd4j.extension.express.domain.model.vo.RuleValidationResult;
import io.ddd4j.extension.express.domain.service.RuleEngineDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 规则引擎领域服务实现
 * 
 * <p>基础设施层：使用QLExpress实现规则执行。
 * 负责规则表达式的执行和语法验证。
 * 
 * <p>注意：此类通过ExpressAutoConfiguration自动配置，无需手动添加@Service注解
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public class RuleEngineDomainServiceImpl implements RuleEngineDomainService {

    private static final Logger log = LoggerFactory.getLogger(RuleEngineDomainServiceImpl.class);

    private final Express4Runner expressRunner;

    public RuleEngineDomainServiceImpl(Express4Runner expressRunner) {
        this.expressRunner = expressRunner;
    }

    /**
     * 执行规则表达式
     * 
     * @param rule 规则定义，不能为null
     * @param context 执行上下文，包含规则表达式中使用的变量
     * @return 规则执行结果，包含执行状态、结果值、错误信息等
     */
    @Override
    public RuleExecutionResult executeRule(RuleDefinition rule, Map<String, Object> context) {
        try {
            // 创建执行上下文
            ExpressContext expressContext = new MapExpressContext(context);

            // 执行表达式
            Object result = expressRunner.execute(rule.getRuleExpression(), expressContext, null);

            return RuleExecutionResult.builder()
                    .success(true)
                    .result(result)
                    .ruleCode(rule.getRuleCode())
                    .executedAt(java.time.LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("执行规则失败: ruleCode={}", rule.getRuleCode(), e);
            return RuleExecutionResult.builder()
                    .success(false)
                    .errorCode("EXECUTION_ERROR")
                    .errorMessage("规则执行异常: " + e.getMessage())
                    .ruleCode(rule.getRuleCode())
                    .executedAt(java.time.LocalDateTime.now())
                    .build();
        }
    }

    /**
     * 验证规则表达式语法
     * 
     * @param expression 规则表达式，QLExpress语法
     * @return 验证结果，包含是否有效和错误信息
     */
    @Override
    public RuleValidationResult validateExpression(String expression) {
        try {
            expressRunner.check(expression, CheckOptions.DEFAULT_OPTIONS);
            return RuleValidationResult.builder()
                    .valid(true)
                    .message("规则语法正确")
                    .build();
        } catch (Exception e) {
            return RuleValidationResult.builder()
                    .valid(false)
                    .message("规则语法错误: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 检查规则是否可用
     * 
     * @param rule 规则定义
     * @return true表示规则可用，false表示规则不可用
     */
    @Override
    public boolean isRuleAvailable(RuleDefinition rule) {
        return rule != null && rule.isAvailable();
    }
}

