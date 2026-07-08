package io.ddd4j.data.crypto.strategy;

import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.ApiCode;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.data.crypto.enums.CryptoType;
import io.ddd4j.data.crypto.enums.SymmetricAlgorithmType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 请求加解密内部服务实现
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class NoOpCryptoStrategy implements CryptoStrategy {

    /** JSON 对象映射器 */
    @Getter
    private ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param objectMapper JSON 对象映射器
     */
    public NoOpCryptoStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 获取加解密方式
     *
     * @return 无操作加解密方式
     */
    @Override
    public CryptoType getType() {
        return CryptoType.NOOP;
    }

    /**
     * 字段加密（无操作，直接返回 JSON 序列化结果）
     *
     * @param value         待加密字段的值
     * @param algorithmType 加密算法类型（忽略）
     * @param encMode       采用的加密模式（忽略）
     * @param padMode       填充模式（忽略）
     * @param key           密钥（忽略）
     * @param iv            初始向量（忽略）
     * @param plainIsEncode 明文是否编码（忽略）
     * @param <T>           字段类型
     * @return JSON 序列化后的字符串
     */
    @Override
    public <T> String encrypt(T value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode) {
        try {
            return getObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            log.error("Json Processing Error : {}", ex.getMessage());
            throw new BizRuntimeException(ApiCode.SC_INTERNAL_SERVER_ERROR, "Json Processing Error");
        }
    }

    /**
     * 字段解密（无操作，直接进行 JSON 反序列化）
     *
     * @param value         待解密字段的值
     * @param algorithmType 加密算法类型（忽略）
     * @param encMode       采用的加密模式（忽略）
     * @param padMode       填充模式（忽略）
     * @param key           密钥（忽略）
     * @param iv            初始向量（忽略）
     * @param plainIsEncode 明文是否编码（忽略）
     * @param rtType        返回值类型
     * @param <T>           字段类型
     * @return 解密后的字段值
     */
    @Override
    public <T> T decrypt(String value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode, Class<T> rtType) {
        try {
            return getObjectMapper().readValue(value, rtType);
        } catch (Exception ex) {
            log.error("Json Processing Error : {}", ex.getMessage());
            throw new BizRuntimeException(ApiCode.SC_INTERNAL_SERVER_ERROR, "Json Processing Error");
        }
    }

    /**
     * HMAC 签名（无操作，返回空字符串）
     *
     * @param value         待签名的值
     * @param hmacAlgorithm HMAC 算法类型（忽略）
     * @param key           密钥（忽略）
     * @param iv            初始向量（忽略）
     * @param plainIsEncode 明文是否编码（忽略）
     * @param <T>           字段类型
     * @return 空字符串
     */
    @Override
    public <T> String hmac(T value, HmacAlgorithm hmacAlgorithm, String key, String iv, boolean plainIsEncode) {
        return "";
    }


}
