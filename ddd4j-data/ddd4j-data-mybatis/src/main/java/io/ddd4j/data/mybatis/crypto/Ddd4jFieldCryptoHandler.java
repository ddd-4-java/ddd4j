package io.ddd4j.data.mybatis.crypto;

import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * ddd4j 字段加密处理器（零 Spring 依赖）。
 *
 * <p>桥接 ddd4j-data-crypto 的 {@link CryptoStrategy} 到 MyBatis 拦截器体系：
 * <ul>
 *   <li>写入时自动加密 {@code @EncryptField} 标注字段</li>
 *   <li>读取时自动解密 {@code @DecryptField} 标注字段</li>
 * </ul>
 *
 * <p>满足等保数据加密要求：SM4 对称加密 + SM3 摘要签名。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public class Ddd4jFieldCryptoHandler {

    private static final Logger log = LoggerFactory.getLogger(Ddd4jFieldCryptoHandler.class);

    private final CryptoStrategy cryptoStrategy;

    public Ddd4jFieldCryptoHandler(CryptoStrategy cryptoStrategy) {
        Objects.requireNonNull(cryptoStrategy, "cryptoStrategy must not be null");
        this.cryptoStrategy = cryptoStrategy;
    }

    /**
     * 加密字段值。
     */
    public <T> T encrypt(T value) {
        if (value == null) {
            return null;
        }
        String encrypted = cryptoStrategy.encrypt(value, null, null, null, null, null, false);
        log.debug("Field encrypted: {} -> {}", value.getClass().getSimpleName(), encrypted);
        return (T) encrypted;
    }

    /**
     * 解密字段值。
     */
    public <T> T decrypt(T value) {
        if (value == null || !(value instanceof String encrypted) || encrypted.isEmpty()) {
            return value;
        }
        try {
            T decrypted = cryptoStrategy.decrypt(encrypted, null, null, null, null, null, false, (Class<T>) value.getClass());
            log.debug("Field decrypted: {} -> {}", encrypted, decrypted);
            return decrypted;
        } catch (Exception e) {
            log.warn("Field decryption failed, returning original value: {}", e.getMessage());
            return value;
        }
    }

    /**
     * 获取底层 CryptoStrategy。
     */
    public CryptoStrategy getCryptoStrategy() {
        return cryptoStrategy;
    }
}
