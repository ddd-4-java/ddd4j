package io.ddd4j.extension.jackson.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Objects;

/**
 * 处理字符串类型的null值
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class NullStringJsonSerializer extends JsonSerializer<Object> {

    public static final NullStringJsonSerializer INSTANCE = new NullStringJsonSerializer();

    @Override
    public void serialize(Object value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (Objects.isNull(value)) {
            jsonGenerator.writeString(StringUtils.EMPTY);
        }
    }

}
