package io.ddd4j.data.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.data.crypto.provider.CryptoProvider;
import io.ddd4j.data.crypto.provider.DefaultCryptoProvider;
import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import io.ddd4j.data.crypto.strategy.DefaultCryptoStrategy;
import io.ddd4j.data.crypto.strategy.FlksecCryptoStrategy;
import io.ddd4j.data.crypto.strategy.NoOpCryptoStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.stream.Collectors;

/**
 * @author wandl
 */
@Configuration(proxyBeanMethods = false)
// @EnableConfigurationProperties(CryptoProperties.class)
public class DefaultCryptoAutoConfiguration {

    @Bean
    public DefaultCryptoProvider cryptoProvider(ObjectProvider<CryptoStrategy> cryptoStrategyProvider, CryptoProperties cryptoProperties) {
        return new DefaultCryptoProvider(cryptoStrategyProvider.stream().collect(Collectors.toList()), cryptoProperties);
    }

    @Bean
    public NoOpCryptoStrategy noOpCryptoStrategy(ObjectProvider<ObjectMapper> objectMapperProvider, CryptoProperties cryptoProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new NoOpCryptoStrategy(objectMapper);
    }

    @Bean
    public DefaultCryptoStrategy defaultCryptoStrategy(ObjectProvider<ObjectMapper> objectMapperProvider, CryptoProperties cryptoProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new DefaultCryptoStrategy(objectMapper);
    }

    @Bean
    public FlksecCryptoStrategy flksecCryptoStrategy(ObjectProvider<ObjectMapper> objectMapperProvider,
                                                     ObjectProvider<RestClient> restClientObjectProvider,
                                                     CryptoProperties cryptoProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        RestClient restClient = restClientObjectProvider.getIfAvailable();
        return new FlksecCryptoStrategy(objectMapper, restClient, cryptoProperties.getFlksecAddress(), cryptoProperties.getFlksecPort());
    }

}
