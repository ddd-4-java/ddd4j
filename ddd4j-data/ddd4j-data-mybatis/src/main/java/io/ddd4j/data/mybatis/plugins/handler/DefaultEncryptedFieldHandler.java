package io.ddd4j.data.mybatis.plugins.handler;

import cn.hutool.crypto.digest.HmacAlgorithm;
import io.ddd4j.data.crypto.enums.SymmetricAlgorithmType;
import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.enhance.crypto.handler.EncryptedFieldHandler;

import java.util.Objects;

/**
 * ddd4j 加密字段处理器（桥接 ddd4j-data-crypto 的 CryptoStrategy）。
 *
 * <p>将 ddd4j-data-crypto 的 {@link CryptoStrategy} 桥接为 mybatis-enhance 的
 * {@link EncryptedFieldHandler}，配合 {@code @EncryptedField} / {@code @EncryptedTable} 注解，
 * 由 {@code DataEncryptionInterceptor} / {@code DataDecryptionInterceptor} 驱动透明加解密。</p>
 *
 * <p>满足等保数据加密要求：SM4 对称加密 + SM3 摘要签名。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class DefaultEncryptedFieldHandler implements EncryptedFieldHandler {

    private final CryptoStrategy cryptoStrategy;

    public DefaultEncryptedFieldHandler(CryptoStrategy cryptoStrategy) {
        this.cryptoStrategy = Objects.requireNonNull(cryptoStrategy, "cryptoStrategy must not be null");
    }

    @Override
    public <T> String encrypt(T value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return cryptoStrategy.encrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false);
    }

    @Override
    public <T> T decrypt(String value, Class<T> rtType) {
        if (StrKit.isEmpty(value)) {
            return null;
        }
        return cryptoStrategy.decrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false, rtType);
    }

    @Override
    public <T> String hmac(T value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return cryptoStrategy.hmac(value, HmacAlgorithm.HmacSM3, null, null, false);
    }
}
