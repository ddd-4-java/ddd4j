package io.ddd4j.data.crypto.provider;

/**
 * 加解密提供者
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
     * \u5b57\u6bb5\u89e3\u5bc6
     * @param value \u5f85\u89e3\u5bc6\u5b57\u6bb5\u7684\u503c
     * @param <T> \u5b57\u6bb5\u7c7b\u578b
     * @return T \u89e3\u5bc6\u540e\u7684\u5b57\u6bb5\u503c
     *
     * @param rtType \u89e3\u5bc6\u7ed3\u679c\u7c7b\u578b
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
