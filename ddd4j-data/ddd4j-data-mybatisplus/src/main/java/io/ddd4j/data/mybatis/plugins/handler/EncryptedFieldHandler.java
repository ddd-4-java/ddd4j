package io.ddd4j.data.mybatis.plugins.handler;

/**
 * MyBatis-Plus 字段透明加密桥接契约。
 *
 * <p>由 ddd4j 自己维护，避免把未发布增强库的接口暴露给应用代码。</p>
 */
public interface EncryptedFieldHandler {

    /**
     * 执行 <code>encrypt</code> 对应的处理。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param value 由当前成员保存或用于计算的值
     * @return 当前操作得到的 <code>String</code> 结果
     */
    <T> String encrypt(T value);

    /**
     * 执行 <code>decrypt</code> 对应的处理。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param value 由当前成员保存或用于计算的值
     * @param resultType 传入此操作的 <code>Class&lt;T&gt;</code> 类型 <code>resultType</code>
     * @return 当前操作得到的 <code>T</code> 结果
     */
    <T> T decrypt(String value, Class<T> resultType);

    /**
     * 执行 <code>hmac</code> 对应的处理。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param value 由当前成员保存或用于计算的值
     * @return 当前操作得到的 <code>String</code> 结果
     */
    <T> String hmac(T value);
}
