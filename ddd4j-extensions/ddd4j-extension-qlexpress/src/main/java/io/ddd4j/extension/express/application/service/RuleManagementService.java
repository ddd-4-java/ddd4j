package io.ddd4j.extension.express.application.service;

import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import io.ddd4j.extension.express.domain.model.vo.RuleValidationResult;
import io.ddd4j.extension.express.domain.repository.RuleDefinitionRepository;
import io.ddd4j.extension.express.domain.service.RuleEngineDomainService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 规则管理服务
 *
 * <p>应用层服务：负责规则的增删改查，同步清理和更新缓存。
 * 所有规则操作都会自动处理缓存同步，确保数据一致性。
 *
 * <p>主要功能：
 * <ul>
 *   <li>规则的创建、更新、删除</li>
 *   <li>规则的启用、禁用</li>
 *   <li>规则查询（按ID、编码、类型等）</li>
 *   <li>规则语法验证</li>
 *   <li>三级查询（本地环境 -> Redis缓存 -> 数据库）</li>
 *   <li>缓存管理</li>
 * </ul>
 *
 * <p>三级查询机制：
 * <ol>
 *   <li>本地环境（内存中的硬编码函数规则，优先级最高）</li>
 *   <li>Redis缓存（二级缓存，提高查询性能）</li>
 *   <li>数据库（持久化存储，最终数据源）</li>
 * </ol>
 *
 * <p>注意：此类通过ExpressAutoConfiguration自动配置，无需手动添加@Service注解
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public class RuleManagementService {

    private final RuleDefinitionRepository ruleRepository;
    private final RuleCacheService ruleCacheService;
    private final RuleEngineDomainService ruleEngineDomainService;

    // 本地环境规则注册表（硬编码函数规则）
    private final java.util.Map<String, RuleDefinition> localRuleRegistry = new java.util.HashMap<>();

    public RuleManagementService(RuleDefinitionRepository ruleRepository,
                                 RuleCacheService ruleCacheService,
                                 RuleEngineDomainService ruleEngineDomainService) {
        this.ruleRepository = ruleRepository;
        this.ruleCacheService = ruleCacheService;
        this.ruleEngineDomainService = ruleEngineDomainService;
        // 初始化本地环境规则（硬编码函数规则）
        initLocalRules();
    }

    /**
     * 初始化本地环境规则（硬编码函数规则）
     * 这些规则逻辑固定，不需要从数据库加载
     */
    private void initLocalRules() {
        // 注册硬编码函数规则
        registerLocalRule("contains", "字符串包含判断", "FUNCTION",
                "io.ddd4j.extension.express.infrastructure.function.ContainsFunction", "CLASS");
        registerLocalRule("startsWith", "字符串开头判断", "FUNCTION",
                "io.ddd4j.extension.express.infrastructure.function.StartsWithFunction", "CLASS");
        registerLocalRule("endsWith", "字符串结尾判断", "FUNCTION",
                "io.ddd4j.extension.express.infrastructure.function.EndsWithFunction", "CLASS");
        registerLocalRule("formatDate", "日期格式化", "FUNCTION",
                "io.ddd4j.extension.express.infrastructure.function.FormatDateFunction", "CLASS");

        log.info("本地环境规则初始化完成，共{}个规则", localRuleRegistry.size());
    }

    /**
     * 注册本地环境规则
     */
    private void registerLocalRule(String ruleCode, String ruleName, String ruleType,
                                   String functionClass, String functionType) {
        RuleDefinition rule = new RuleDefinition();
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleName);
        rule.setRuleType(ruleType);
        rule.setFunctionClass(functionClass);
        rule.setFunctionType(functionType);
        rule.setEnabled(true);
        rule.setPriority(1000); // 本地规则优先级最高

        localRuleRegistry.put(ruleCode, rule);
    }

    /**
     * 创建规则
     *
     * <p>创建新规则，执行以下步骤：
     * <ol>
     *   <li>验证规则语法</li>
     *   <li>保存到数据库</li>
     *   <li>同步更新缓存（如果规则启用）</li>
     * </ol>
     *
     * @param rule 规则定义，不能为null，ruleCode不能为空
     * @return 保存后的规则定义
     * @throws IllegalArgumentException 如果规则语法错误或ruleCode已存在
     */
    public RuleDefinition createRule(RuleDefinition rule) {
        // 1. 验证规则语法
        RuleValidationResult validationResult = ruleEngineDomainService.validateExpression(rule.getRuleExpression());
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("规则语法错误: " + validationResult.getMessage());
        }

        // 2. 设置创建时间和更新时间
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());

        // 3. 保存到数据库
        RuleDefinition savedRule = ruleRepository.save(rule);

        // 4. 同步更新缓存
        if (savedRule.isAvailable()) {
            ruleCacheService.put(savedRule.getRuleCode(), savedRule);
            log.info("创建规则并更新缓存: {}", savedRule.getRuleCode());
        }

        return savedRule;
    }

    /**
     * 更新规则
     *
     * <p>更新现有规则，执行以下步骤：
     * <ol>
     *   <li>验证规则语法</li>
     *   <li>更新数据库</li>
     *   <li>同步更新或清除缓存（根据规则状态）</li>
     * </ol>
     *
     * @param id   规则ID，不能为null
     * @param rule 新的规则定义，不能为null
     * @return 更新后的规则定义
     * @throws IllegalArgumentException 如果规则不存在或规则语法错误
     */
    public RuleDefinition updateRule(Long id, RuleDefinition rule) {
        Optional<RuleDefinition> existingRuleOpt = ruleRepository.findById(id);
        if (!existingRuleOpt.isPresent()) {
            throw new IllegalArgumentException("规则不存在: " + id);
        }

        RuleDefinition existingRule = existingRuleOpt.get();

        // 1. 验证规则语法
        RuleValidationResult validationResult = ruleEngineDomainService.validateExpression(rule.getRuleExpression());
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("规则语法错误: " + validationResult.getMessage());
        }

        // 2. 更新规则属性
        existingRule.setRuleName(rule.getRuleName());
        existingRule.setRuleExpression(rule.getRuleExpression());
        existingRule.setRuleDescription(rule.getRuleDescription());
        existingRule.setRuleType(rule.getRuleType());
        existingRule.setEnabled(rule.getEnabled());
        existingRule.setPriority(rule.getPriority());
        existingRule.setUpdatedAt(LocalDateTime.now());

        // 3. 保存到数据库
        RuleDefinition savedRule = ruleRepository.save(existingRule);

        // 4. 同步更新缓存
        if (savedRule.isAvailable()) {
            ruleCacheService.put(savedRule.getRuleCode(), savedRule);
            log.info("更新规则并更新缓存: {}", savedRule.getRuleCode());
        } else {
            // 如果禁用，清除缓存
            ruleCacheService.evict(savedRule.getRuleCode());
            log.info("更新规则（已禁用）并清除缓存: {}", savedRule.getRuleCode());
        }

        return savedRule;
    }

    /**
     * 删除规则
     *
     * <p>删除规则，执行以下步骤：
     * <ol>
     *   <li>删除数据库记录</li>
     *   <li>同步清除缓存</li>
     * </ol>
     *
     * @param id 规则ID，不能为null
     * @throws IllegalArgumentException 如果规则不存在
     */
    public void deleteRule(Long id) {
        Optional<RuleDefinition> rule = ruleRepository.findById(id);
        if (rule.isPresent()) {
            String ruleCode = rule.get().getRuleCode();
            ruleRepository.deleteById(id);
            // 同步清除缓存
            ruleCacheService.evict(ruleCode);
            log.info("删除规则并清除缓存: {}", ruleCode);
        } else {
            throw new IllegalArgumentException("规则不存在: " + id);
        }
    }

    /**
     * 根据ID查询规则
     *
     * @param id 规则ID，不能为null
     * @return 规则定义，如果不存在返回Optional.empty()
     */
    public Optional<RuleDefinition> getRuleById(Long id) {
        return ruleRepository.findById(id);
    }

    /**
     * 根据规则编码查询规则（三级查询：本地环境 -> Redis缓存 -> 数据库）
     *
     * <p>查询顺序：
     * <ol>
     *   <li>本地环境（内存中的硬编码规则，优先级最高）</li>
     *   <li>Redis缓存（二级缓存，提高查询性能）</li>
     *   <li>数据库（持久化存储，最终数据源）</li>
     * </ol>
     *
     * @param ruleCode 规则编码，不能为null
     * @return 规则定义，如果不存在返回Optional.empty()
     */
    public Optional<RuleDefinition> getRuleByCode(String ruleCode) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            return Optional.empty();
        }

        // 第一级：本地环境查询（硬编码规则）
        RuleDefinition localRule = localRuleRegistry.get(ruleCode);
        if (localRule != null) {
            log.debug("从本地环境获取规则: {}", ruleCode);
            return Optional.of(localRule);
        }

        // 第二级：Redis缓存查询
        RuleDefinition cachedRule = ruleCacheService.get(ruleCode);
        if (cachedRule != null) {
            log.debug("从Redis缓存获取规则: {}", ruleCode);
            return Optional.of(cachedRule);
        }

        // 第三级：数据库查询
        Optional<RuleDefinition> rule = ruleRepository.findByRuleCode(ruleCode);
        if (rule.isPresent() && rule.get().isAvailable()) {
            // 存入缓存
            ruleCacheService.put(ruleCode, rule.get());
            log.debug("从数据库获取规则并存入缓存: {}", ruleCode);
        }
        return rule;
    }

    /**
     * 获取所有启用的规则（包括本地和数据库）
     *
     * <p>合并本地硬编码规则和数据库中的规则，按优先级降序排列。
     * 如果存在同名规则，本地规则优先级更高（不会被覆盖）。
     *
     * @return 所有启用的规则列表，按优先级从高到低排序
     */
    public List<RuleDefinition> getAllEnabledRules() {
        // 获取数据库中的启用规则
        List<RuleDefinition> dbRules = ruleRepository.findEnabledRulesOrderByPriorityDesc();

        // 合并本地规则（本地规则优先级更高）
        java.util.Map<String, RuleDefinition> allRules = new java.util.HashMap<>(localRuleRegistry);
        dbRules.forEach(rule -> {
            // 如果本地已存在同名规则，不覆盖（本地规则优先级更高）
            allRules.putIfAbsent(rule.getRuleCode(), rule);
        });

        return allRules.values().stream()
                .filter(RuleDefinition::isAvailable)
                .sorted((r1, r2) -> {
                    int priorityCompare = Integer.compare(
                            r2.getPriority() != null ? r2.getPriority() : 0,
                            r1.getPriority() != null ? r1.getPriority() : 0
                    );
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    return r1.getRuleCode().compareTo(r2.getRuleCode());
                })
                .toList();
    }

    /**
     * 获取所有函数规则（ruleType 为 FUNCTION）
     *
     * @return 所有函数规则列表，按优先级从高到低排序
     */
    public List<RuleDefinition> getAllFunctionRules() {
        return getAllEnabledRules().stream()
                .filter(rule -> "FUNCTION".equals(rule.getRuleType()))
                .toList();
    }

    /**
     * 查询所有规则
     *
     * @return 所有规则列表，包括启用和禁用的规则
     */
    public List<RuleDefinition> getAllRules() {
        return ruleRepository.findAll();
    }

    /**
     * 查询所有启用的规则，按优先级降序排列
     *
     * @return 启用的规则列表，按优先级从高到低排序
     */
    public List<RuleDefinition> getEnabledRules() {
        return ruleRepository.findEnabledRulesOrderByPriorityDesc();
    }

    /**
     * 根据规则类型查询启用的规则
     *
     * @param ruleType 规则类型，如DECISION、VALIDATION、CALCULATION等
     * @return 指定类型的启用规则列表
     */
    public List<RuleDefinition> getRulesByType(String ruleType) {
        return ruleRepository.findByRuleTypeAndEnabled(ruleType);
    }

    /**
     * 启用规则
     *
     * <p>将规则状态设置为启用，并同步更新缓存。
     *
     * @param id 规则ID，不能为null
     * @return 更新后的规则定义
     * @throws IllegalArgumentException 如果规则不存在
     */
    public RuleDefinition enableRule(Long id) {
        Optional<RuleDefinition> ruleOpt = ruleRepository.findById(id);
        if (!ruleOpt.isPresent()) {
            throw new IllegalArgumentException("规则不存在: " + id);
        }

        RuleDefinition rule = ruleOpt.get();
        rule.enable();
        RuleDefinition savedRule = ruleRepository.save(rule);

        // 同步更新缓存
        ruleCacheService.put(savedRule.getRuleCode(), savedRule);
        log.info("启用规则并更新缓存: {}", savedRule.getRuleCode());

        return savedRule;
    }

    /**
     * 禁用规则
     *
     * <p>将规则状态设置为禁用，并同步清除缓存。
     * 禁用的规则不会被执行。
     *
     * @param id 规则ID，不能为null
     * @return 更新后的规则定义
     * @throws IllegalArgumentException 如果规则不存在
     */
    public RuleDefinition disableRule(Long id) {
        Optional<RuleDefinition> ruleOpt = ruleRepository.findById(id);
        if (!ruleOpt.isPresent()) {
            throw new IllegalArgumentException("规则不存在: " + id);
        }

        RuleDefinition rule = ruleOpt.get();
        rule.disable();
        RuleDefinition savedRule = ruleRepository.save(rule);

        // 同步清除缓存
        ruleCacheService.evict(savedRule.getRuleCode());
        log.info("禁用规则并清除缓存: {}", savedRule.getRuleCode());

        return savedRule;
    }

    /**
     * 清除规则缓存
     *
     * @param ruleCode 规则编码，不能为null
     */
    public void clearRuleCache(String ruleCode) {
        ruleCacheService.evict(ruleCode);
        log.info("清除规则缓存: {}", ruleCode);
    }

    /**
     * 清除所有规则缓存
     *
     * <p>清除Redis中所有规则相关的缓存数据，谨慎使用。
     * 建议在规则批量更新后使用。
     */
    public void clearAllRuleCache() {
        ruleCacheService.evictAll();
        log.info("清除所有规则缓存");
    }
}
