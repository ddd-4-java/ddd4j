package io.ddd4j.mq.event;

/**
 * MQ 事件序列化/反序列化契约。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface MQEventSerialization {

    /**
     * \u53cd\u5e8f\u5217\u5316\u6d88\u606f\u4f53\u4e3a\u9886\u57df\u5bf9\u8c61\u3002
     * @param src \u539f\u59cb\u6d88\u606f\u4f53
     * @param dist \u76ee\u6807\u7c7b\u578b
     * @param <S> \u6e90\u7c7b\u578b
     * @param <T> \u76ee\u6807\u7c7b\u578b
     * @return \u53cd\u5e8f\u5217\u5316\u7ed3\u679c
     *
     * @throws RuntimeException \u4e8b\u4ef6\u8f7d\u8377\u65e0\u6cd5\u53cd\u5e8f\u5217\u5316
     */
    <S, T> T deserialize(S src, Class<T> dist) throws RuntimeException;

    /**
     * \u5e8f\u5217\u5316\u9886\u57df\u5bf9\u8c61\u4e3a\u6d88\u606f\u4f53\u3002
     * @param src \u6e90\u5bf9\u8c61
     * @param <T> \u5e8f\u5217\u5316\u7ed3\u679c\u7c7b\u578b
     * @return \u5e8f\u5217\u5316\u7ed3\u679c
     *
     * @throws RuntimeException \u4e8b\u4ef6\u65e0\u6cd5\u5e8f\u5217\u5316
     */
    <T> T serialize(Object src) throws RuntimeException;
}
