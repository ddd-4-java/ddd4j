package io.ddd4j.extension.jackson.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import io.ddd4j.extension.jackson.annotation.Sensitive;
import io.ddd4j.extension.jackson.annotation.SensitiveStrategy;

import java.io.IOException;
import java.util.Objects;

/**
 * 数据脱敏 JSON 序列化器
 *
 * <p>根据 {@link Sensitive} 注解中指定的脱敏策略，对字符串字段进行脱敏序列化。
 * 支持上下文感知，可在运行时动态获取字段上的注解信息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.8.RELEASE
 */
public class SensitiveJsonSerializer extends JsonSerializer<String> implements ContextualSerializer {
    /**
     * 脱敏策略，由注解 {@link Sensitive#strategy()} 动态设置
     */
    private SensitiveStrategy strategy;

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(strategy.desensitizer().apply(value));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {

        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (Objects.nonNull(annotation) && Objects.equals(String.class, property.getType().getRawClass())) {
            this.strategy = annotation.strategy();
            return this;
        }
        return prov.findValueSerializer(property.getType(), property);

    }
}