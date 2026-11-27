package io.ddd4j.data.mybatis.plugins.handler;

import cn.hutool.crypto.digest.HmacAlgorithm;
import io.ddd4j.data.crypto.enums.SymmetricAlgorithmType;
import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * ddd4j 加密字段处理器（桥接 ddd4j-data-crypto 的 CryptoStrategy）。
 *
 * <p>将 ddd4j-data-crypto 的 {@link CryptoStrategy} 桥接为 ddd4j 的
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

    /** 保存 <code>crypto Strategy</code> 对应的 <code>CryptoStrategy</code> 数据。 */
    private final CryptoStrategy cryptoStrategy;

    /**
     * 初始化 <code>DefaultEncryptedFieldHandler</code> 使用的 <code>cryptoStrategy</code>。
     *
     * @param cryptoStrategy 传入此操作的 <code>CryptoStrategy</code> 类型 <code>cryptoStrategy</code>
     */
    public DefaultEncryptedFieldHandler(CryptoStrategy cryptoStrategy) {
        this.cryptoStrategy = Objects.requireNonNull(cryptoStrategy, "cryptoStrategy must not be null");
    }

    /**
     * 执行 <code>encrypt</code>，调用 <code>cryptoStrategy.encrypt</code>。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param value 由当前成员保存或用于计算的值
     * @return 按条件返回 <code>null</code>、<code>cryptoStrategy.encrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false)</code>
     */
    @Override
    public <T> String encrypt(T value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return cryptoStrategy.encrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false);
    }

    /**
     * 执行 <code>decrypt</code>，调用 <code>StrKit.isEmpty</code>、<code>cryptoStrategy.decrypt</code>。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param value 由当前成员保存或用于计算的值
     * @param rtType 传入此操作的 <code>Class&lt;T&gt;</code> 类型 <code>rtType</code>
     * @return 按条件返回 <code>null</code>、<code>cryptoStrategy.decrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false, rtType)</code>
     */
    @Override
    public <T> T decrypt(String value, Class<T> rtType) {
        if (StrKit.isEmpty(value)) {
            return null;
        }
        return cryptoStrategy.decrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false, rtType);
    }

    /**
     * 执行 <code>hmac</code>，调用 <code>cryptoStrategy.hmac</code>。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param value 由当前成员保存或用于计算的值
     * @return 按条件返回 <code>null</code>、<code>cryptoStrategy.hmac(value, HmacAlgorithm.HmacSM3, null, null, false)</code>
     */
    @Override
    public <T> String hmac(T value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return cryptoStrategy.hmac(value, HmacAlgorithm.HmacSM3, null, null, false);
    }
}
