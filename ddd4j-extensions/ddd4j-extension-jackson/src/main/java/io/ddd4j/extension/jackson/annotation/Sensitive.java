package io.ddd4j.extension.jackson.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.ddd4j.extension.jackson.ser.SensitiveJsonSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据脱敏注解
 *
 * <p>标注在需要脱敏处理的字段上，配合 {@link SensitiveJsonSerializer} 实现自动脱敏。
 * 支持用户名、身份证、手机号、地址、邮箱等多种脱敏策略。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveJsonSerializer.class)
public @interface Sensitive {

    SensitiveStrategy strategy();
}