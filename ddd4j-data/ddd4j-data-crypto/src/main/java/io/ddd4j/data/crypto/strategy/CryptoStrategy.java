package io.ddd4j.data.crypto.strategy;

import cn.hutool.crypto.digest.HmacAlgorithm;
import io.ddd4j.data.crypto.domain.enums.CryptoType;
import io.ddd4j.data.crypto.domain.enums.SymmetricAlgorithmType;

/**
 * 请求内容的传输机密性和完整性所需的加解密策略
 */
public interface CryptoStrategy {

    /**
     * 获取加解密方式
     *
     * @return 加解密方式
     */
    CryptoType getType();

    /**
     * 字段加密
     *
     * @param value         待加密字段的值
     * @param algorithmType 加密算法类型，系统支持 SM4【必须】
     * @param encMode       采用的加密模式，系统支持 ecb,cbc,cfb,ofb，推荐 cbc，国密不允许使用 ecb 模式【必须】
     * @param padMode       加密运算所采用的填充模式，系统支持 PKCS5Padding 和 NoPadding，推荐 PKCS5Padding【必须】
     * @param key           Base64 格式的密钥字符串【必须】
     * @param iv            Base64 格式的初始向量，加密模式为 cbc,cfb,ofb 时该参数不能为空，解码后长度 为 16 位，可自定义【非必须】
     * @param plainIsEncode 明文是否进行了 base64 编码 true/false 【非必须】
     * @param <T>           字段类型
     * @return T 加密后的字段值
     */
    <T> String encrypt(T value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode);

    /**
     * 字段解密
     *
     * @param value         待解密字段的值
     * @param algorithmType 加密算法类型，系统支持 SM4【必须】
     * @param encMode       采用的加密模式，系统支持 ecb,cbc,cfb,ofb，推荐 cbc，国密不允许使用 ecb 模式【必须】
     * @param padMode       加密运算所采用的填充模式，系统支持 PKCS5Padding 和 NoPadding，推荐 PKCS5Padding【必须】
     * @param key           Base64 格式的密钥字符串【必须】
     * @param iv            Base64 格式的初始向量，加密模式为 cbc,cfb,ofb 时该参数不能为空，解码后长度 为 16 位，可自定义【非必须】
     * @param plainIsEncode 明文是否进行了 base64 编码 true/false 【非必须】
     * @param rtType        返回值类型
     * @param <T>           字段类型
     * @return T 解密后的字段值
     */
    <T> T decrypt(String value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode, Class<T> rtType);

    /**
     * hmac 签名
     *
     * @param value         待签名的值
     * @param key           Base64 格式的密钥字符串【必须】
     * @param iv            Base64 格式的初始向量，加密模式为 cbc,cfb,ofb 时该参数不能为空，解码后长度 为 16 位，可自定义【非必须】
     * @param plainIsEncode 明文是否进行了 base64 编码 true/false 【非必须】
     * @param <T>           字段类型
     * @return 签名后的字符串
     */
    <T> String hmac(T value, HmacAlgorithm hmacAlgorithm, String key, String iv, boolean plainIsEncode);

}
