package io.ddd4j.extension.express.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 测试规则请求DTO
 * 
 * <p>应用层DTO：用于规则测试接口的请求数据传输对象。
 * 
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRuleRequest {

    /** 规则编码，用于指定要测试的规则 */
    private String ruleCode;
    
    /** 执行上下文，包含规则表达式中使用的变量 */
    private Map<String, Object> context;
}

