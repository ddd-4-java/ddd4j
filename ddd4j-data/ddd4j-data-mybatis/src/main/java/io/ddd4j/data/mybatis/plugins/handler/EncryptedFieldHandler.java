package io.ddd4j.data.mybatis.plugins.handler;

/**
 * 字段透明加密桥接契约。
 *
 * <p>具体 MyBatis 拦截器可消费本接口；它不依赖未发布的第三方增强库。</p>
 */
public interface EncryptedFieldHandler {

    <T> String encrypt(T value);

    <T> T decrypt(String value, Class<T> resultType);

    <T> String hmac(T value);
}
