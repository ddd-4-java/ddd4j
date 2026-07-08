package io.ddd4j.data.crypto.provider;

import io.ddd4j.data.crypto.CryptoProperties;
import io.ddd4j.data.crypto.enums.CryptoType;
import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 默认加解密提供者实现
 * <p>根据配置的加密类型，委派对应的加解密策略执行加密、解密和签名操作</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class DefaultCryptoProvider implements CryptoProvider {

    /** 加解密策略映射表 */
    private final EnumMap<CryptoType, CryptoStrategy> enumMap = new EnumMap<>(CryptoType.class);
    /** 加密配置属性 */
    private final CryptoProperties cryptoProperties;

    /**
     * 构造函数
     *
     * @param cryptoStrategies 加解密策略列表
     * @param cryptoProperties 加密配置属性
     */
    public DefaultCryptoProvider(List<CryptoStrategy> cryptoStrategies, CryptoProperties cryptoProperties) {
        enumMap.putAll(cryptoStrategies.stream().collect(Collectors.toMap(CryptoStrategy::getType, strategy -> strategy)));
        this.cryptoProperties = cryptoProperties;
    }

    /**
     * 字段加密
     *
     * @param value 待加密的字段值
     * @param <T>   字段类型
     * @return 加密后的字符串
     */
    @Override
    public <T> String encrypt(T value) {
        CryptoType provider = Optional.ofNullable(cryptoProperties.getType()).orElse(CryptoType.NOOP);
        CryptoStrategy cryptoStrategy = enumMap.get(provider);
        log.debug("CryptoType：{}, Encrypt Strategy : {}", provider, cryptoStrategy);
        if (Objects.isNull(cryptoStrategy)) {
            throw new IllegalArgumentException("CryptoStrategy not found");
        }
        return cryptoStrategy.encrypt(value, cryptoProperties.getSymmetricAlgorithm(),
                cryptoProperties.getMode().name(), cryptoProperties.getPadding().name(), cryptoProperties.getKey(), cryptoProperties.getIv(),
                cryptoProperties.isPlainIsEncode());
    }

    /**
     * 字段解密
     *
     * @param value 待解密的字符串
     * @param rtType 返回值类型
     * @param <T>    字段类型
     * @return 解密后的字段值
     */
    @Override
    public <T> T decrypt(String value, Class<T> rtType) {
        CryptoType provider = Optional.ofNullable(cryptoProperties.getType()).orElse(CryptoType.NOOP);
        CryptoStrategy cryptoStrategy = enumMap.get(provider);
        log.debug("CryptoType：{}, Decrypt Strategy : {}", provider, cryptoStrategy);
        if (Objects.isNull(cryptoStrategy)) {
            throw new IllegalArgumentException("CryptoStrategy not found");
        }
        return cryptoStrategy.decrypt(value, cryptoProperties.getSymmetricAlgorithm(),
                cryptoProperties.getMode().name(), cryptoProperties.getPadding().name(), cryptoProperties.getKey(), cryptoProperties.getIv(),
                cryptoProperties.isPlainIsEncode(), rtType);
    }

    /**
     * HMAC 签名
     *
     * @param value 待签名的值
     * @param <T>   字段类型
     * @return 签名后的字符串
     */
    @Override
    public <T> String hmac(T value) {
        CryptoType provider = Optional.ofNullable(cryptoProperties.getType()).orElse(CryptoType.NOOP);
        CryptoStrategy cryptoStrategy = enumMap.get(provider);
        log.debug("CryptoType：{}, Hmac Strategy : {}", provider, cryptoStrategy);
        if (Objects.isNull(cryptoStrategy)) {
            throw new IllegalArgumentException("CryptoStrategy not found");
        }
        return cryptoStrategy.hmac(value, cryptoProperties.getHmacAlgorithm(), cryptoProperties.getKey(), cryptoProperties.getIv(),
                cryptoProperties.isPlainIsEncode());
    }


}
