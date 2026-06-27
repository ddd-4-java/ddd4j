package io.ddd4j.data.crypto.provider;


import io.ddd4j.data.crypto.CryptoProperties;
import io.ddd4j.data.crypto.domain.enums.CryptoType;
import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DefaultCryptoProvider implements CryptoProvider {

    private final EnumMap<CryptoType, CryptoStrategy> enumMap = new EnumMap<>(CryptoType.class);
    private CryptoProperties cryptoProperties;

    public DefaultCryptoProvider(List<CryptoStrategy> cryptoStrategies, CryptoProperties cryptoProperties) {
        enumMap.putAll(cryptoStrategies.stream().collect(Collectors.toMap(CryptoStrategy::getType, strategy -> strategy)));
        this.cryptoProperties = cryptoProperties;
    }

    @Override
    public <T> String encrypt(T value) {
        CryptoType provider = Optional.ofNullable(cryptoProperties.getType()).orElse(CryptoType.NOOP);
        CryptoStrategy cryptoStrategy = enumMap.get(provider);
        log.debug("CryptoType：{}, Encrypt Strategy : {}", provider, cryptoStrategy);
        if (cryptoStrategy == null) {
            throw new IllegalArgumentException("CryptoStrategy not found");
        }
        return cryptoStrategy.encrypt(value, cryptoProperties.getSymmetricAlgorithm(),
                cryptoProperties.getMode().name(), cryptoProperties.getPadding().name(), cryptoProperties.getKey(), cryptoProperties.getIv(),
                cryptoProperties.isPlainIsEncode());
    }

    @Override
    public <T> T decrypt(String value, Class<T> rtType) {
        CryptoType provider = Optional.ofNullable(cryptoProperties.getType()).orElse(CryptoType.NOOP);
        CryptoStrategy cryptoStrategy = enumMap.get(provider);
        log.debug("CryptoType：{}, Decrypt Strategy : {}", provider, cryptoStrategy);
        if (cryptoStrategy == null) {
            throw new IllegalArgumentException("CryptoStrategy not found");
        }
        return cryptoStrategy.decrypt(value, cryptoProperties.getSymmetricAlgorithm(),
                cryptoProperties.getMode().name(), cryptoProperties.getPadding().name(), cryptoProperties.getKey(), cryptoProperties.getIv(),
                cryptoProperties.isPlainIsEncode(), rtType);
    }

    @Override
    public <T> String hmac(T value) {
        CryptoType provider = Optional.ofNullable(cryptoProperties.getType()).orElse(CryptoType.NOOP);
        CryptoStrategy cryptoStrategy = enumMap.get(provider);
        log.debug("CryptoType：{}, Hmac Strategy : {}", provider, cryptoStrategy);
        if (cryptoStrategy == null) {
            throw new IllegalArgumentException("CryptoStrategy not found");
        }
        return cryptoStrategy.hmac(value, cryptoProperties.getHmacAlgorithm(), cryptoProperties.getKey(), cryptoProperties.getIv(),
                cryptoProperties.isPlainIsEncode());
    }


}
