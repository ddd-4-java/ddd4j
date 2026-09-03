package io.ddd4j.mq.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.event.MQEventSerialization;

import java.util.Objects;

/**
 * 默认 JSON 消息序列化实现（JDK8 轴：直接持有 Jackson 2 ObjectMapper，
 * 与 2.0.x {@code kit.lang.JsonKit} 静态门面语义对齐）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class JsonMQEventSerialization implements MQEventSerialization {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public <S, T> T deserialize(S src, Class<T> dist) throws RuntimeException {
        if (Objects.isNull(src)) {
            return null;
        }
        String text;
        if (src instanceof String) {
            text = (String) src;
        } else {
            text = String.valueOf(src);
        }
        if (StrKit.isEmpty(text)) {
            return null;
        }
        try {
            return MAPPER.readValue(text, dist);
        } catch (Exception e) {
            throw new IllegalStateException("MQ event JSON deserialize failed: " + dist.getName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T serialize(Object src) throws RuntimeException {
        if (Objects.isNull(src)) {
            return null;
        }
        if (src instanceof String) {
            return (T) src;
        }
        try {
            return (T) MAPPER.writeValueAsString(src);
        } catch (Exception e) {
            throw new IllegalStateException("MQ event JSON serialize failed: " + src.getClass().getName(), e);
        }
    }
}
