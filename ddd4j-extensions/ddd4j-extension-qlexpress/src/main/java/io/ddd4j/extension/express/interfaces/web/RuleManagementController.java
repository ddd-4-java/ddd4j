package io.ddd4j.extension.express.interfaces.web;

import io.ddd4j.extension.express.application.dto.CreateRuleRequest;
import io.ddd4j.extension.express.application.dto.RuleMapper;
import io.ddd4j.extension.express.application.dto.RuleResponse;
import io.ddd4j.extension.express.application.dto.UpdateRuleRequest;
import io.ddd4j.extension.express.application.service.RuleEngineApplicationService;
import io.ddd4j.extension.express.application.service.RuleManagementService;
import io.ddd4j.extension.express.domain.model.vo.RuleExecutionResult;
import io.ddd4j.extension.express.domain.model.vo.RuleValidationResult;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 规则管理服务
 *
 * <p>接口层：提供规则管理的应用入口。
 * 提供规则的增删改查、启用禁用、测试执行、语法验证等功能。
 *
 * <p>该类为纯 Java 版本，去除了对 Spring MVC 的依赖。
 * 实际接入 Web 框架（Spring MVC/WebFlux 等）时，可在此基础上进行包装：
 * 注入 {@link RuleEngineApplicationService}、{@link RuleManagementService}、{@link RuleMapper}
 * 后暴露为 REST API（如 {@code /api/rules}）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public class RuleManagementController {

    private final RuleEngineApplicationService ruleEngineApplicationService;
    private final RuleManagementService ruleManagementService;
    private final RuleMapper ruleMapper;

    public RuleManagementController(RuleEngineApplicationService ruleEngineApplicationService,
                                    RuleManagementService ruleManagementService,
                                    RuleMapper ruleMapper) {
        this.ruleEngineApplicationService = ruleEngineApplicationService;
        this.ruleManagementService = ruleManagementService;
        this.ruleMapper = ruleMapper;
    }

    /**
     * 查询规则列表
     *
     * @param ruleType 规则类型（可选，为空或空白时返回全部）
     * @return 规则列表
     */
    public List<RuleResponse> listRules(String ruleType) {
        if (StrKit.hasText(ruleType)) {
            return ruleManagementService.getRulesByType(ruleType).stream()
                    .map(ruleMapper::toResponse)
                    .collect(Collectors.toList());
        }
        return ruleManagementService.getAllRules().stream()
                .map(ruleMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取规则详情
     *
     * @param id 规则ID
     * @return 规则详情，不存在返回 {@link Optional#empty()}
     */
    public Optional<RuleResponse> getRule(Long id) {
        return ruleManagementService.getRuleById(id)
                .map(ruleMapper::toResponse);
    }

    /**
     * 根据规则编码获取规则详情（带缓存）
     *
     * @param ruleCode 规则编码
     * @return 规则详情，不存在返回 {@link Optional#empty()}
     */
    public Optional<RuleResponse> getRuleByCode(String ruleCode) {
        return ruleManagementService.getRuleByCode(ruleCode)
                .map(ruleMapper::toResponse);
    }

    /**
     * 创建规则
     *
     * <p>自动验证规则语法，保存到数据库，并同步更新缓存。
     *
     * @param request 创建规则请求DTO
     * @return 创建后的规则信息
     */
    public RuleResponse createRule(CreateRuleRequest request) {
        var rule = ruleMapper.toEntity(request);
        var savedRule = ruleManagementService.createRule(rule);
        return ruleMapper.toResponse(savedRule);
    }

    /**
     * 更新规则
     *
     * <p>自动验证规则语法，更新数据库，并同步更新缓存。
     *
     * @param id      规则ID
     * @param request 更新规则请求DTO
     * @return 更新后的规则信息
     * @throws IllegalArgumentException 规则不存在时抛出
     */
    public RuleResponse updateRule(Long id, UpdateRuleRequest request) {
        var rule = ruleManagementService.getRuleById(id)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + id));
        ruleMapper.updateEntity(rule, request);
        var updatedRule = ruleManagementService.updateRule(id, rule);
        return ruleMapper.toResponse(updatedRule);
    }

    /**
     * 删除规则
     *
     * <p>自动删除数据库记录，并同步清除缓存。
     *
     * @param id 规则ID
     * @throws IllegalArgumentException 规则不存在时抛出
     */
    public void deleteRule(Long id) {
        ruleManagementService.deleteRule(id);
    }

    /**
     * 启用规则
     *
     * <p>同步更新缓存。
     *
     * @param id 规则ID
     * @return 启用后的规则信息
     * @throws IllegalArgumentException 规则不存在时抛出
     */
    public RuleResponse enableRule(Long id) {
        var rule = ruleManagementService.enableRule(id);
        return ruleMapper.toResponse(rule);
    }

    /**
     * 禁用规则
     *
     * <p>同步清除缓存。
     *
     * @param id 规则ID
     * @return 禁用后的规则信息
     * @throws IllegalArgumentException 规则不存在时抛出
     */
    public RuleResponse disableRule(Long id) {
        var rule = ruleManagementService.disableRule(id);
        return ruleMapper.toResponse(rule);
    }

    /**
     * 测试规则
     *
     * @param ruleCode 规则编码
     * @param context  执行上下文，包含规则表达式中使用的变量
     * @return 规则执行结果
     */
    public RuleExecutionResult testRule(String ruleCode, Map<String, Object> context) {
        Objects.requireNonNull(ruleCode, "ruleCode 不能为空");
        return ruleEngineApplicationService.executeRule(ruleCode, context);
    }

    /**
     * 验证规则语法
     *
     * @param expression 规则表达式
     * @return 验证结果
     */
    public RuleValidationResult validateRule(String expression) {
        return ruleEngineApplicationService.validateRule(expression);
    }

    /**
     * 清除指定规则缓存
     *
     * @param ruleCode 规则编码
     * @return 清除结果提示信息
     */
    public String clearRuleCache(String ruleCode) {
        ruleManagementService.clearRuleCache(ruleCode);
        return "规则缓存清除成功: " + ruleCode;
    }

    /**
     * 清除所有规则缓存
     *
     * @return 清除结果提示信息
     */
    public String clearAllCache() {
        ruleManagementService.clearAllRuleCache();
        return "所有规则缓存清除成功";
    }
}
