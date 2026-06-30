package io.ddd4j.guice.data.crypto;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.ddd4j.data.crypto.CryptoProperties;
import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import io.ddd4j.data.crypto.strategy.DefaultCryptoStrategy;
import jakarta.inject.Singleton;

/**
 * ddd4j 加解密的 Guice 桥接模块。
 */
public class Ddd4jCryptoGuiceModule extends AbstractModule {

    @Provides
    @Singleton
    public CryptoProperties cryptoProperties() {
        return new CryptoProperties();
    }

    @Provides
    @Singleton
    public CryptoStrategy defaultCryptoStrategy(CryptoProperties properties) {
        return new DefaultCryptoStrategy(null);
    }
}
