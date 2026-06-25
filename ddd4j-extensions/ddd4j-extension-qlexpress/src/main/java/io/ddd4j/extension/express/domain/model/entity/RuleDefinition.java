package io.ddd4j.extension.express.domain.model.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 规则定义实体
 * 
 * <p>领域模型：规则的核心实体，包含规则的业务属性。
 * 规则定义包含规则编码、表达式、类型、状态等核心信息。
 * 
 * <p>规则可以是两种形式：
 * <ul>
 *   <li>表达式规则：使用 ruleExpression 字段存储 QLExpress 表达式</li>
 *   <li>函数规则：使用 functionClass/functionMethod 字段存储函数类信息，用于动态加载自定义函数</li>
 * </ul>
 * 
 * <p>规则类型说明：
 * <ul>
 *   <li>DECISION - 决策规则：用于业务决策判断</li>
 *   <li>VALIDATION - 校验规则：用于数据校验</li>
 *   <li>CALCULATION - 计算规则：用于数值计算</li>
 *   <li>FUNCTION - 函数规则：用于定义可复用的自定义函数</li>
 * </ul>
 * 
 * <p>函数类型说明（当 ruleType 为 FUNCTION 时）：
 * <ul>
 *   <li>CLASS - 类方法：通过反射加载类方法</li>
 *   <li>SCRIPT - 脚本函数：通过脚本定义函数（未来扩展）</li>
 * </ul>
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public class RuleDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;
    
    /** 规则ID值对象 */
    private RuleId ruleId;
    
    /** 规则编码（唯一标识），用于规则查找和执行 */
    private String ruleCode;
    
    /** 规则名称，用于展示和描述 */
    private String ruleName;
    
    /** 规则表达式，QLExpress语法 */
    private String ruleExpression;
    
    /** 规则描述，说明规则的用途和使用场景 */
    private String ruleDescription;
    
    /** 规则类型（DECISION-决策规则，VALIDATION-校验规则，CALCULATION-计算规则等） */
    private String ruleType;
    
    /** 是否启用，true表示规则可用，false表示规则已禁用 */
    private Boolean enabled;
    
    /** 优先级，数值越大优先级越高，用于规则冲突时的选择 */
    private Integer priority;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    // ========== 函数相关字段（当规则类型为 FUNCTION 时使用） ==========
    
    /** 函数类名（全限定名），用于动态加载自定义函数 */
    private String functionClass;
    
    /** 函数方法名（如果是静态方法），用于反射调用 */
    private String functionMethod;
    
    /** 函数脚本（如果是脚本函数），用于脚本函数定义 */
    private String functionScript;
    
    /** 函数类型（CLASS-类方法，SCRIPT-脚本函数），仅在 ruleType 为 FUNCTION 时有效 */
    private String functionType;
    
    /** 返回类型，用于函数签名定义 */
    private String returnType;
    
    /** 参数类型（逗号分隔），用于函数签名定义 */
    private String parameterTypes;

    public RuleDefinition() {
    }

    /**
     * 使用RuleId的构造函数
     * 
     * @param ruleId 规则ID值对象
     * @param ruleCode 规则编码，不能为null
     * @param ruleName 规则名称
     * @param ruleExpression 规则表达式，QLExpress语法
     * @param ruleDescription 规则描述
     * @param ruleType 规则类型
     * @param enabled 是否启用
     * @param priority 优先级，数值越大优先级越高
     */
    public RuleDefinition(RuleId ruleId, String ruleCode, String ruleName, String ruleExpression, 
                          String ruleDescription, String ruleType, Boolean enabled, Integer priority) {
        this.ruleId = ruleId;
        this.id = ruleId != null ? ruleId.toLong() : null;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.ruleExpression = ruleExpression;
        this.ruleDescription = ruleDescription;
        this.ruleType = ruleType;
        this.enabled = enabled;
        this.priority = priority;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 便捷构造函数（兼容旧代码，使用Long id）
     * 
     * @param id 主键ID
     * @param ruleCode 规则编码，不能为null
     * @param ruleName 规则名称
     * @param ruleExpression 规则表达式，QLExpress语法
     * @param ruleDescription 规则描述
     * @param ruleType 规则类型
     * @param enabled 是否启用
     * @param priority 优先级，数值越大优先级越高
     */
    public RuleDefinition(Long id, String ruleCode, String ruleName, String ruleExpression, 
                          String ruleDescription, String ruleType, Boolean enabled, Integer priority) {
        this(id != null ? RuleId.valueOf(id) : null, ruleCode, ruleName, ruleExpression, 
             ruleDescription, ruleType, enabled, priority);
    }

    /**
     * 启用规则
     * 
     * <p>将规则状态设置为启用，并更新更新时间。
     */
    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 禁用规则
     * 
     * <p>将规则状态设置为禁用，并更新更新时间。
     * 禁用的规则不会被执行。
     */
    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新规则表达式
     * 
     * @param expression 新的规则表达式，QLExpress语法，不能为null
     */
    public void updateExpression(String expression) {
        this.ruleExpression = expression;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 检查规则是否可用
     * 
     * <p>规则可用的条件：enabled字段不为null且为true。
     * 
     * @return true表示规则可用，false表示规则不可用
     */
    public boolean isAvailable() {
        return enabled != null && enabled;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
        if (id != null) {
            this.ruleId = RuleId.valueOf(id);
        }
    }

    public RuleId getRuleId() {
        return ruleId;
    }

    public void setRuleId(RuleId ruleId) {
        this.ruleId = ruleId;
        if (ruleId != null) {
            this.id = ruleId.toLong();
        }
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleExpression() {
        return ruleExpression;
    }

    public void setRuleExpression(String ruleExpression) {
        this.ruleExpression = ruleExpression;
    }

    public String getRuleDescription() {
        return ruleDescription;
    }

    public void setRuleDescription(String ruleDescription) {
        this.ruleDescription = ruleDescription;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ========== 函数相关字段的 Getters and Setters ==========
    
    /**
     * 获取函数类名
     * 
     * @return 函数类名（全限定名）
     */
    public String getFunctionClass() {
        return functionClass;
    }

    /**
     * 设置函数类名
     * 
     * @param functionClass 函数类名（全限定名）
     */
    public void setFunctionClass(String functionClass) {
        this.functionClass = functionClass;
    }

    /**
     * 获取函数方法名
     * 
     * @return 函数方法名
     */
    public String getFunctionMethod() {
        return functionMethod;
    }

    /**
     * 设置函数方法名
     * 
     * @param functionMethod 函数方法名
     */
    public void setFunctionMethod(String functionMethod) {
        this.functionMethod = functionMethod;
    }

    /**
     * 获取函数脚本
     * 
     * @return 函数脚本
     */
    public String getFunctionScript() {
        return functionScript;
    }

    /**
     * 设置函数脚本
     * 
     * @param functionScript 函数脚本
     */
    public void setFunctionScript(String functionScript) {
        this.functionScript = functionScript;
    }

    /**
     * 获取函数类型
     * 
     * @return 函数类型（CLASS-类方法，SCRIPT-脚本函数）
     */
    public String getFunctionType() {
        return functionType;
    }

    /**
     * 设置函数类型
     * 
     * @param functionType 函数类型（CLASS-类方法，SCRIPT-脚本函数）
     */
    public void setFunctionType(String functionType) {
        this.functionType = functionType;
    }

    /**
     * 获取返回类型
     * 
     * @return 返回类型
     */
    public String getReturnType() {
        return returnType;
    }

    /**
     * 设置返回类型
     * 
     * @param returnType 返回类型
     */
    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    /**
     * 获取参数类型
     * 
     * @return 参数类型（逗号分隔）
     */
    public String getParameterTypes() {
        return parameterTypes;
    }

    /**
     * 设置参数类型
     * 
     * @param parameterTypes 参数类型（逗号分隔）
     */
    public void setParameterTypes(String parameterTypes) {
        this.parameterTypes = parameterTypes;
    }
    
    /**
     * 判断是否为函数规则
     * 
     * @return true表示是函数规则，false表示是表达式规则
     */
    public boolean isFunctionRule() {
        return "FUNCTION".equals(ruleType) && functionClass != null && !functionClass.trim().isEmpty();
    }
    
    /**
     * 判断是否为表达式规则
     * 
     * @return true表示是表达式规则，false表示是函数规则
     */
    public boolean isExpressionRule() {
        return ruleExpression != null && !ruleExpression.trim().isEmpty();
    }
}
