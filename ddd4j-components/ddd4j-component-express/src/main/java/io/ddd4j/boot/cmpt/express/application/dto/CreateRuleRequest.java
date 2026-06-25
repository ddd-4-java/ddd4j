package io.ddd4j.boot.cmpt.express.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建规则请求DTO
 * 
 * <p>应用层DTO：用于接口层接收创建规则的请求数据传输对象。
 * 遵循DDD规范，不直接使用领域实体，只包含创建规则所需的字段。
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRuleRequest {

    /** 规则编码（唯一标识），必填 */
    @NotBlank(message = "规则编码不能为空")
    private String ruleCode;
    
    /** 规则名称，必填 */
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;
    
    /** 规则表达式，QLExpress语法（表达式规则必填） */
    private String ruleExpression;
    
    /** 规则描述 */
    private String ruleDescription;
    
    /** 规则类型（DECISION-决策规则，VALIDATION-校验规则，CALCULATION-计算规则，FUNCTION-函数规则），必填 */
    @NotBlank(message = "规则类型不能为空")
    private String ruleType;
    
    /** 是否启用，默认true */
    private Boolean enabled = true;
    
    /** 优先级，数值越大优先级越高，默认0 */
    private Integer priority = 0;
    
    // ========== 函数相关字段（当规则类型为 FUNCTION 时使用） ==========
    
    /** 函数类名（全限定名），函数规则必填 */
    private String functionClass;
    
    /** 函数方法名（如果是静态方法） */
    private String functionMethod;
    
    /** 函数脚本（如果是脚本函数） */
    private String functionScript;
    
    /** 函数类型（CLASS-类方法，SCRIPT-脚本函数），函数规则必填 */
    private String functionType;
    
    /** 返回类型 */
    private String returnType;
    
    /** 参数类型（逗号分隔） */
    private String parameterTypes;
}

