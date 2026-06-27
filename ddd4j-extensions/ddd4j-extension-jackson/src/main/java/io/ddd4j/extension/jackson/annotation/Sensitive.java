package io.ddd4j.extension.jackson.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.ddd4j.extension.jackson.ser.SensitiveJsonSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveJsonSerializer.class)
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public @interface Sensitive {

    SensitiveStrategy strategy();
}