package io.ddd4j.data.crypto.strategy;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.ApiCode;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.data.crypto.enums.CryptoType;
import io.ddd4j.data.crypto.enums.SymmetricAlgorithmType;
import io.ddd4j.kit.crypto.SymmetricCryptoKit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 请求加解密内部服务实现
 * 传输机密性：SM4（国密对称加密算法）
 * 传输完整性：SM3（国密摘要签名算法）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class DefaultCryptoStrategy implements CryptoStrategy {

    /** JSON 对象映射器 */
    @Getter
    private ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param objectMapper JSON 对象映射器
     */
    public DefaultCryptoStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 获取加解密方式
     *
     * @return 内部加解密方式
     */
    @Override
    public CryptoType getType() {
        return CryptoType.INTERNAL;
    }

    /**
     * 字段加密
     *
     * @param value         待加密字段的值
     * @param algorithmType 加密算法类型
     * @param encMode       采用的加密模式
     * @param padMode       填充模式
     * @param key           Base64 格式的密钥字符串
     * @param iv            Base64 格式的初始向量
     * @param plainIsEncode 明文是否进行了 base64 编码
     * @param <T>           字段类型
     * @return 加密后的字符串
     */
    @Override
    public <T> String encrypt(T value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode) {
        try {
            // 1、序列化Value
            String valueAsString = getObjectMapper().writeValueAsString(value);
            log.debug("Plain Value To {} Encrypt: {}", algorithmType.getName(), valueAsString);
            // 2、获取加密器
            SymmetricCrypto crypto = SymmetricCryptoKit.getSymmetricCrypto(algorithmType.getName(), encMode, padMode, Base64.decodeStr(key), Objects.isNull(iv) ? null : Base64.decodeStr(iv));
            // 3、加密Value，如果 plainIsEncode =true 则对加密结果进行Base64
            if (plainIsEncode) {
                valueAsString = crypto.encryptBase64(valueAsString);
            } else {
                valueAsString = new String(crypto.encrypt(valueAsString), StandardCharsets.UTF_8);
            }
            log.debug("{} Encrypt Value : {}", algorithmType.getName(), valueAsString);
            return valueAsString;
        } catch (Exception ex) {
            log.error("{} Encrypt Error : {}", algorithmType.getName(), ex.getMessage());
            throw new BizRuntimeException(ApiCode.SC_INTERNAL_SERVER_ERROR, algorithmType.getName() + " Encrypt Error");
        }
    }

    /**
     * 字段解密
     *
     * @param value         待解密字段的值
     * @param algorithmType 加密算法类型
     * @param encMode       采用的加密模式
     * @param padMode       填充模式
     * @param key           Base64 格式的密钥字符串
     * @param iv            Base64 格式的初始向量
     * @param plainIsEncode 明文是否进行了 base64 编码
     * @param rtType        返回值类型
     * @param <T>           字段类型
     * @return 解密后的字段值
     */
    @Override
    public <T> T decrypt(String value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode, Class<T> rtType) {
        try {
            log.debug("Plain Value to {} Decrypt : {}", algorithmType.getName(), value);
            // 1、获取解密器
            SymmetricCrypto crypto = SymmetricCryptoKit.getSymmetricCrypto(algorithmType.getName(), encMode, padMode, Base64.decodeStr(key), Objects.isNull(iv) ? null : Base64.decodeStr(iv));
            // 2、解密请求体
            String decryptStr = crypto.decryptStr(value);
            log.debug("{} Decrypt Value : {}", algorithmType.getName(), decryptStr);
            return getObjectMapper().readValue(decryptStr, rtType);
        } catch (Exception ex) {
            log.error("{} Decrypt Error : {}", algorithmType.getName(), ex.getMessage());
            throw new BizRuntimeException(ApiCode.SC_INTERNAL_SERVER_ERROR, algorithmType.getName() + " Decrypt Error");
        }
    }

    /**
     * HMAC 签名
     *
     * @param value         待签名的值
     * @param hmacAlgorithm HMAC 算法类型
     * @param key           Base64 格式的密钥字符串
     * @param iv            Base64 格式的初始向量
     * @param plainIsEncode 明文是否进行了 base64 编码
     * @param <T>           字段类型
     * @return 签名后的字符串
     */
    @Override
    public <T> String hmac(T value, HmacAlgorithm hmacAlgorithm, String key, String iv, boolean plainIsEncode) {
        try {
            log.debug("Plain Value to {} HMAC : {}", hmacAlgorithm.name(), value);
            HMac hMac = SymmetricCryptoKit.getHmac(hmacAlgorithm, Base64.decodeStr(key));
            String hmacValue;
            if (plainIsEncode) {
                hmacValue = hMac.digestBase64(getObjectMapper().writeValueAsString(value), StandardCharsets.UTF_8, Boolean.TRUE);
            } else {
                hmacValue = new String(hMac.digest(getObjectMapper().writeValueAsString(value)), StandardCharsets.UTF_8);
            }
            log.debug("HMAC Digest Value : {}", hmacValue);
            return hmacValue;
        } catch (Exception ex) {
            log.error("HMAC Digest Error : {}", ex.getMessage());
            throw new BizRuntimeException(ApiCode.SC_INTERNAL_SERVER_ERROR, "HMAC Digest Error");
        }
    }

}
