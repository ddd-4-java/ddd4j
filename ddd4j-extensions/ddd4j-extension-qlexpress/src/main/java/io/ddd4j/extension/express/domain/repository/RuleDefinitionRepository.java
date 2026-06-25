package io.ddd4j.extension.express.domain.repository;

import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 规则定义仓储接口
 * 
 * <p>领域层接口：定义规则持久化的抽象。
 * 不依赖具体的持久化技术（JPA、MyBatis等），由基础设施层实现。
 * 
 * <p>实现类需要根据项目的持久化方案来实现：
 * <ul>
 *   <li>如果使用JPA：实现JpaRepository接口</li>
 *   <li>如果使用MyBatis：实现Mapper接口</li>
 *   <li>如果使用其他方案：直接实现此接口</li>
 * </ul>
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public interface RuleDefinitionRepository {

    /**
     * 根据规则编码查找规则
     * 
     * @param ruleCode 规则编码，不能为null
     * @return 规则定义，如果不存在返回Optional.empty()
     */
    Optional<RuleDefinition> findByRuleCode(String ruleCode);

    /**
     * 查找所有启用的规则，按优先级降序排列
     * 
     * @return 启用的规则列表，按优先级从高到低排序
     */
    List<RuleDefinition> findEnabledRulesOrderByPriorityDesc();

    /**
     * 根据规则类型查找启用的规则
     * 
     * @param ruleType 规则类型，如DECISION、VALIDATION、CALCULATION、FUNCTION等
     * @return 指定类型的启用规则列表
     */
    List<RuleDefinition> findByRuleTypeAndEnabled(String ruleType);

    /**
     * 保存规则
     * 
     * @param rule 规则定义，不能为null
     * @return 保存后的规则定义
     */
    RuleDefinition save(RuleDefinition rule);

    /**
     * 根据ID查找规则
     * 
     * @param id 规则ID，不能为null
     * @return 规则定义，如果不存在返回Optional.empty()
     */
    Optional<RuleDefinition> findById(Long id);

    /**
     * 删除规则
     * 
     * @param id 规则ID，不能为null
     */
    void deleteById(Long id);

    /**
     * 查找所有规则
     * 
     * @return 所有规则列表，包括启用和禁用的规则
     */
    List<RuleDefinition> findAll();
}

