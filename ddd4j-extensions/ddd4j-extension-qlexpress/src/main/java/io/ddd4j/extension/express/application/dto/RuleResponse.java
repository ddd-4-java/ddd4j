package io.ddd4j.extension.express.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 规则响应DTO
 * 
 * <p>应用层DTO：用于接口层返回规则信息的响应数据传输对象。
 * 遵循DDD规范，不直接暴露领域实体，只包含需要展示给客户端的字段。
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResponse {

    /** 主键ID */
    private Long id;
    
    /** 规则编码（唯一标识） */
    private String ruleCode;
    
    /** 规则名称 */
    private String ruleName;
    
    /** 规则表达式，QLExpress语法 */
    private String ruleExpression;
    
    /** 规则描述 */
    private String ruleDescription;
    
    /** 规则类型（DECISION-决策规则，VALIDATION-校验规则，CALCULATION-计算规则，FUNCTION-函数规则） */
    private String ruleType;
    
    /** 是否启用 */
    private Boolean enabled;
    
    /** 优先级，数值越大优先级越高 */
    private Integer priority;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    // ========== 函数相关字段（当规则类型为 FUNCTION 时使用） ==========
    
    /** 函数类名（全限定名） */
    private String functionClass;
    
    /** 函数方法名（如果是静态方法） */
    private String functionMethod;
    
    /** 函数脚本（如果是脚本函数） */
    private String functionScript;
    
    /** 函数类型（CLASS-类方法，SCRIPT-脚本函数） */
    private String functionType;
    
    /** 返回类型 */
    private String returnType;
    
    /** 参数类型（逗号分隔） */
    private String parameterTypes;
}

