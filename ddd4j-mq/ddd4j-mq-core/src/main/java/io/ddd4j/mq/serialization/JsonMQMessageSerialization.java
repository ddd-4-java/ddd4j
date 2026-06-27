package io.ddd4j.mq.serialization;

import io.ddd4j.kit.lang.JsonKit;

/**
 * 默认 JSON 消息序列化实现。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class JsonMQMessageSerialization implements MQMessageSerialization, MQEventSerialization {

    @Override
    public <S, T> T deserialize(S src, Class<T> dist) throws RuntimeException {
        if (src == null) {
            return null;
        }
        String text = src instanceof String s ? s : String.valueOf(src);
        if (text.isEmpty()) {
            return null;
        }
        return JsonKit.toObject(text, dist);
    }

    @Override
    public String serialize(Object src) throws RuntimeException {
        return JsonKit.toJson(src);
    }
}
