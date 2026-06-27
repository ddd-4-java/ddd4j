package io.ddd4j.extension.express.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证规则请求DTO
 * 
 * <p>应用层DTO：用于规则验证接口的请求数据传输对象。
 * 
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateRuleRequest {

    /** 规则表达式，QLExpress语法，需要验证的表达式 */
    private String expression;
}