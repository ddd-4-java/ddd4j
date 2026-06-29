package io.ddd4j.extension.express.interfaces.web;

import io.ddd4j.extension.express.application.dto.*;
import io.ddd4j.extension.express.application.service.RuleEngineApplicationService;
import io.ddd4j.extension.express.application.service.RuleManagementService;
import io.ddd4j.extension.express.domain.model.vo.RuleExecutionResult;
import io.ddd4j.extension.express.domain.model.vo.RuleValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则管理Controller
 *
 * <p>接口层：提供规则管理的REST API。
 * 提供规则的增删改查、启用禁用、测试执行、语法验证等功能。
 *
 * <p>API路径：/api/rules
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/rules")
@Slf4j
public class RuleManagementController {

    @Autowired
    private RuleEngineApplicationService ruleEngineApplicationService;

    @Autowired
    private RuleManagementService ruleManagementService;

    @Autowired
    private RuleMapper ruleMapper;

    /**
     * 查询规则列表
     *
     * @param ruleType 规则类型（可选），如果提供则按类型过滤
     * @return 规则列表
     */
    @GetMapping
    public ResponseEntity<List<RuleResponse>> listRules(
            @RequestParam(required = false) String ruleType) {
        List<RuleResponse> rules;
        if (StringUtils.hasText(ruleType)) {
            rules = ruleManagementService.getRulesByType(ruleType).stream()
                    .map(ruleMapper::toResponse)
                    .collect(Collectors.toList());
        } else {
            rules = ruleManagementService.getAllRules().stream()
                    .map(ruleMapper::toResponse)
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(rules);
    }

    /**
     * 获取规则详情
     *
     * @param id 规则ID
     * @return 规则详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<RuleResponse> getRule(@PathVariable Long id) {
        return ruleManagementService.getRuleById(id)
                .map(ruleMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据规则编码获取规则详情（带缓存）
     *
     * @param ruleCode 规则编码
     * @return 规则详情
     */
    @GetMapping("/code/{ruleCode}")
    public ResponseEntity<RuleResponse> getRuleByCode(@PathVariable String ruleCode) {
        return ruleManagementService.getRuleByCode(ruleCode)
                .map(ruleMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建规则
     *
     * <p>自动验证规则语法，保存到数据库，并同步更新缓存。
     *
     * @param request 创建规则请求DTO
     * @return 创建后的规则信息
     */
    @PostMapping
    public ResponseEntity<RuleResponse> createRule(@RequestBody CreateRuleRequest request) {
        var rule = ruleMapper.toEntity(request);
        var savedRule = ruleManagementService.createRule(rule);
        return ResponseEntity.ok(ruleMapper.toResponse(savedRule));
    }

    /**
     * 更新规则
     *
     * <p>自动验证规则语法，更新数据库，并同步更新缓存。
     *
     * @param id      规则ID
     * @param request 更新规则请求DTO
     * @return 更新后的规则信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<RuleResponse> updateRule(@PathVariable Long id, @RequestBody UpdateRuleRequest request) {
        try {
            var rule = ruleManagementService.getRuleById(id)
                    .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + id));
            ruleMapper.updateEntity(rule, request);
            var updatedRule = ruleManagementService.updateRule(id, rule);
            return ResponseEntity.ok(ruleMapper.toResponse(updatedRule));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 删除规则
     * 自动删除数据库记录，并同步清除缓存
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        try {
            ruleManagementService.deleteRule(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 启用规则
     *
     * <p>同步更新缓存。
     *
     * @param id 规则ID
     * @return 启用后的规则信息
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<RuleResponse> enableRule(@PathVariable Long id) {
        try {
            var rule = ruleManagementService.enableRule(id);
            return ResponseEntity.ok(ruleMapper.toResponse(rule));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 禁用规则
     *
     * <p>同步清除缓存。
     *
     * @param id 规则ID
     * @return 禁用后的规则信息
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<RuleResponse> disableRule(@PathVariable Long id) {
        try {
            var rule = ruleManagementService.disableRule(id);
            return ResponseEntity.ok(ruleMapper.toResponse(rule));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 测试规则
     */
    @PostMapping("/test")
    public ResponseEntity<RuleExecutionResult> testRule(@RequestBody TestRuleRequest request) {
        RuleExecutionResult result = ruleEngineApplicationService.executeRule(
                request.getRuleCode(), request.getContext());
        return ResponseEntity.ok(result);
    }

    /**
     * 验证规则语法
     */
    @PostMapping("/validate")
    public ResponseEntity<RuleValidationResult> validateRule(@RequestBody ValidateRuleRequest request) {
        RuleValidationResult result = ruleEngineApplicationService.validateRule(request.getExpression());
        return ResponseEntity.ok(result);
    }

    /**
     * 清除指定规则缓存
     */
    @PostMapping("/cache/clear/{ruleCode}")
    public ResponseEntity<String> clearRuleCache(@PathVariable String ruleCode) {
        ruleManagementService.clearRuleCache(ruleCode);
        return ResponseEntity.ok("规则缓存清除成功: " + ruleCode);
    }

    /**
     * 清除所有规则缓存
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearAllCache() {
        ruleManagementService.clearAllRuleCache();
        return ResponseEntity.ok("所有规则缓存清除成功");
    }
}