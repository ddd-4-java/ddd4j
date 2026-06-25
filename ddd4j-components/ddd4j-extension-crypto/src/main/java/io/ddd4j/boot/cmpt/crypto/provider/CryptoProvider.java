package io.ddd4j.boot.cmpt.crypto.provider;

/**
 * 加解密提供者
 */
public interface CryptoProvider {

    /**
     * 字段加密
     *
     * @param value 待加密字段的值
     * @param <T>   字段类型
     * @return T 加密后的字段值
     */
    <T> String encrypt(T value);

    /**
     * 字段解密
     *
     * @param value 待解密字段的值
     * @param <T>   字段类型
     * @return T 解密后的字段值
     */
    <T> T decrypt(String value, Class<T> rtType);

    /**
     * hmac 签名
     *
     * @param value 待签名的值
     * @param <T>   字段类型
     * @return 签名后的字符串
     */
    <T> String hmac(T value);

}
