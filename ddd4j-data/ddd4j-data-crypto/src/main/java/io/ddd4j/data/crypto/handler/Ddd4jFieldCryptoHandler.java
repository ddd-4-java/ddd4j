package io.ddd4j.data.crypto.handler;

import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * ORM 无关的 ddd4j 字段加解密处理器。
 *
 * <p>MyBatis、MyBatis-Plus 和 JPA 适配器共享该处理器，避免加密能力依赖具体 ORM。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Slf4j
@SuppressWarnings("unchecked")
public class Ddd4jFieldCryptoHandler {

    private final CryptoStrategy cryptoStrategy;

    public Ddd4jFieldCryptoHandler(CryptoStrategy cryptoStrategy) {
        this.cryptoStrategy = Objects.requireNonNull(cryptoStrategy, "cryptoStrategy must not be null");
    }

    public <T> T encrypt(T value) {
        if (Objects.isNull(value)) {
            return null;
        }
        String encrypted = cryptoStrategy.encrypt(value, null, null, null, null, null, false);
        log.debug("Field encrypted: {}", value.getClass().getSimpleName());
        return (T) encrypted;
    }

    public <T> T decrypt(T value) {
        if (!(value instanceof String encrypted) || StrKit.isEmpty(encrypted)) {
            return value;
        }
        try {
            return cryptoStrategy.decrypt(encrypted, null, null, null, null, null, false,
                    (Class<T>) value.getClass());
        } catch (Exception exception) {
            log.warn("Field decryption failed, returning original value: {}", exception.getMessage());
            return value;
        }
    }

    public CryptoStrategy getCryptoStrategy() {
        return cryptoStrategy;
    }
}
