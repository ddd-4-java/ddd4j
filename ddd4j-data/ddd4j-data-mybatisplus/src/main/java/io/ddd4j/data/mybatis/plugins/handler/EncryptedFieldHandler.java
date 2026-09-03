package io.ddd4j.data.mybatis.plugins.handler;

/**
 * MyBatis-Plus 字段透明加密桥接契约。
 *
 * <p>由 ddd4j 自己维护，避免把未发布增强库的接口暴露给应用代码。</p>
 */
public interface EncryptedFieldHandler {

    <T> String encrypt(T value);

    <T> T decrypt(String value, Class<T> resultType);

    <T> String hmac(T value);
}
