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

    /** \u8d1f\u8d23\u5b57\u6bb5\u52a0\u89e3\u5bc6\u8fd0\u7b97\u7684\u7b56\u7565\u3002 */
    private final CryptoStrategy cryptoStrategy;

    /**
     * \u521b\u5efa\u5171\u4eab\u7684\u5b57\u6bb5\u52a0\u89e3\u5bc6\u5904\u7406\u5668\u3002
     * @param cryptoStrategy \u52a0\u89e3\u5bc6\u7b56\u7565
     */
    public Ddd4jFieldCryptoHandler(CryptoStrategy cryptoStrategy) {
        this.cryptoStrategy = Objects.requireNonNull(cryptoStrategy, "cryptoStrategy must not be null");
    }

    /**
     * \u52a0\u5bc6\u5b57\u6bb5\u503c\uff1b\u7a7a\u503c\u539f\u6837\u8fd4\u56de\u3002
     * @param value \u539f\u59cb\u5b57\u6bb5\u503c
     * @param <T> \u5b57\u6bb5\u7c7b\u578b
     * @return \u52a0\u5bc6\u540e\u7684\u5b57\u6bb5\u503c
     */
    public <T> T encrypt(T value) {
        if (Objects.isNull(value)) {
            return null;
        }
        String encrypted = cryptoStrategy.encrypt(value, null, null, null, null, null, false);
        log.debug("Field encrypted: {}", value.getClass().getSimpleName());
        return (T) encrypted;
    }

    /**
     * \u89e3\u5bc6\u5b57\u6bb5\u503c\uff1b\u89e3\u5bc6\u5931\u8d25\u65f6\u8fd4\u56de\u539f\u503c\u3002
     * @param value \u52a0\u5bc6\u540e\u7684\u5b57\u6bb5\u503c
     * @param <T> \u5b57\u6bb5\u7c7b\u578b
     * @return \u89e3\u5bc6\u540e\u7684\u5b57\u6bb5\u503c\u6216\u539f\u503c
     */
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

    /**
     * \u8fd4\u56de\u5f53\u524d\u52a0\u89e3\u5bc6\u7b56\u7565\u3002
     * @return \u52a0\u89e3\u5bc6\u7b56\u7565
     */
    public CryptoStrategy getCryptoStrategy() {
        return cryptoStrategy;
    }
}
