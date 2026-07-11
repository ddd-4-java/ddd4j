package io.ddd4j.data.mybatis.plugins.handler;

import cn.hutool.crypto.digest.HmacAlgorithm;
import com.baomidou.mybatisplus.enhance.crypto.handler.EncryptedFieldHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.data.crypto.enums.SymmetricAlgorithmType;
import io.ddd4j.data.crypto.strategy.CryptoStrategy;

/**
 * 将 ddd4j-data-crypto 的 {@link CryptoStrategy} 桥接为 mybatis-plus-enhance 的 {@link EncryptedFieldHandler}。
 *
 * <p>业务方只需提供 {@link CryptoStrategy} 实现（DefaultCryptoStrategy / FlksecCryptoStrategy / NoOpCryptoStrategy），
 * 本适配器自动对接到 enhance 的 @EncryptedField 注解驱动体系：
 * <ul>
 *   <li>{@code @EncryptedField} 标注的 PO 字段，写入时自动加密、读取时自动解密</li>
 *   <li>配合 DataEncryptionInnerInterceptor / DataDecryptionInnerInterceptor 实现透明加解密</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DefaultEncryptedFieldHandler implements EncryptedFieldHandler {

    private final CryptoStrategy cryptoStrategy;

    public DefaultEncryptedFieldHandler(CryptoStrategy cryptoStrategy) {
        this.cryptoStrategy = cryptoStrategy;
    }

    @Override
    public <T> String encrypt(T value) {
        return cryptoStrategy.encrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false);
    }

    @Override
    public <T> T decrypt(String value, Class<T> rtType) {
        return cryptoStrategy.decrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false, rtType);
    }

    @Override
    public <T> String hmac(T value) {
        return cryptoStrategy.hmac(value, HmacAlgorithm.HmacSM3, null, null, false);
    }

}
