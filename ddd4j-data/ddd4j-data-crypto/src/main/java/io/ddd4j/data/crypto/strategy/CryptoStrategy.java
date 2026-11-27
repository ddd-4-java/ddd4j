package io.ddd4j.data.crypto.strategy;

import cn.hutool.crypto.digest.HmacAlgorithm;
import io.ddd4j.data.crypto.enums.CryptoType;
import io.ddd4j.data.crypto.enums.SymmetricAlgorithmType;

/**
 * 请求内容的传输机密性和完整性所需的加解密策略
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
     * hmac \u7b7e\u540d
     * @param value \u5f85\u7b7e\u540d\u7684\u503c
     * @param key Base64 \u683c\u5f0f\u7684\u5bc6\u94a5\u5b57\u7b26\u4e32\u3010\u5fc5\u987b\u3011
     * @param iv Base64 \u683c\u5f0f\u7684\u521d\u59cb\u5411\u91cf\uff0c\u52a0\u5bc6\u6a21\u5f0f\u4e3a cbc,cfb,ofb \u65f6\u8be5\u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a\uff0c\u89e3\u7801\u540e\u957f\u5ea6 \u4e3a 16 \u4f4d\uff0c\u53ef\u81ea\u5b9a\u4e49\u3010\u975e\u5fc5\u987b\u3011
     * @param plainIsEncode \u660e\u6587\u662f\u5426\u8fdb\u884c\u4e86 base64 \u7f16\u7801 true/false \u3010\u975e\u5fc5\u987b\u3011
     * @param <T> \u5b57\u6bb5\u7c7b\u578b
     * @return \u7b7e\u540d\u540e\u7684\u5b57\u7b26\u4e32
     *
     * @param hmacAlgorithm HMAC \u7b97\u6cd5
     */
    <T> String hmac(T value, HmacAlgorithm hmacAlgorithm, String key, String iv, boolean plainIsEncode);

}
