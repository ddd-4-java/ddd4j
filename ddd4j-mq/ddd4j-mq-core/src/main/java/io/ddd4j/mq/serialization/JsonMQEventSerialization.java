package io.ddd4j.mq.serialization;

import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.event.MQEventSerialization;

import java.util.Objects;

/**
 * 默认 JSON 消息序列化实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class JsonMQEventSerialization implements MQEventSerialization {

    /**
     * 从消息通道载荷还原事件。
     *
     * <p>此实现调用 <code>String.valueOf(src)</code>。</p>
     *
     * @param <S> 源类型
     * @param <T> 目标类型
     * @param src 原始消息体
     * @param dist 目标类型
     * @return 根据执行路径返回<code>null</code>、<code>JsonKit.toObject(text, dist)</code>
     * @throws RuntimeException 当前操作无法正常完成时
     */
    @Override
    public <S, T> T deserialize(S src, Class<T> dist) throws RuntimeException {
        if (Objects.isNull(src)) {
            return null;
        }
        String text = src instanceof String s ? s : String.valueOf(src);
        if (StrKit.isEmpty(text)) {
            return null;
        }
        return JsonKit.toObject(text, dist);
    }

    /**
     * 将传入消息编码为发布通道使用的格式。
     *
     * <p>此实现调用 <code>JsonKit.toJson(src)</code>。</p>
     *
     * @param <T> 序列化结果类型
     * @param src 源对象
     * @return 实际返回<code>(T)JsonKit.toJson(src)</code>
     * @throws RuntimeException 当前操作无法正常完成时
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T serialize(Object src) throws RuntimeException {
        return (T) JsonKit.toJson(src);
    }
}
