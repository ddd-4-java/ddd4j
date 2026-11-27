package io.ddd4j.data.mybatis.plugins.handler;

/**
 * 字段透明加密桥接契约。
 *
 * <p>具体 MyBatis 拦截器可消费本接口；它不依赖未发布的第三方增强库。</p>
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
