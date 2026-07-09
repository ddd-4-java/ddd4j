package io.ddd4j.data.mybatis.plugins.handler;

import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.data.crypto.enums.SymmetricAlgorithmType;
import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * ddd4j 加密字段处理器（桥接 ddd4j-data-crypto 的 CryptoStrategy）。
 *
 * <p>将 ddd4j-data-crypto 的 {@link CryptoStrategy} 桥接为加密字段处理逻辑，
 * 配合 {@link io.ddd4j.data.mybatis.crypto.EncryptField} 注解使用。
 *
 * <p>满足等保数据加密要求：SM4 对称加密 + SM3 摘要签名。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class DefaultEncryptedFieldHandler {

    private final CryptoStrategy cryptoStrategy;
    private final ObjectMapper objectMapper;

    public DefaultEncryptedFieldHandler(CryptoStrategy cryptoStrategy, ObjectMapper objectMapper) {
        this.cryptoStrategy = cryptoStrategy;
        this.objectMapper = objectMapper;
    }

    public <T> String encrypt(T value) {
        return cryptoStrategy.encrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false);
    }

    public <T> T decrypt(String value, Class<T> rtType) {
        return cryptoStrategy.decrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false, rtType);
    }

    public <T> String hmac(T value) {
        return cryptoStrategy.hmac(value, HmacAlgorithm.HmacSM3, null, null, false);
    }
}
