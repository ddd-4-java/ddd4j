package io.ddd4j.extension.jackson.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Objects;

/**
 * 处理数值类型的null值
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class NullNumberJsonSerializer extends JsonSerializer<Object> {

    public static final NullNumberJsonSerializer INSTANCE = new NullNumberJsonSerializer();

    @Override
    public void serialize(Object value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (Objects.isNull(value)) {
            jsonGenerator.writeNumber(0);
        }
    }

}
