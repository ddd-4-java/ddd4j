package io.ddd4j.extension.jackson.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Objects;

/**
 * JSON null 值序列化器
 *
 * <p>当序列化的值为 null 时，输出 null 值。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class NullJsonSerializer extends JsonSerializer<Object> {

    public static final NullJsonSerializer INSTANCE = new NullJsonSerializer();

    @Override
    public void serialize(Object value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (Objects.isNull(value)) {
            jsonGenerator.writeNull();
        }
    }

}
