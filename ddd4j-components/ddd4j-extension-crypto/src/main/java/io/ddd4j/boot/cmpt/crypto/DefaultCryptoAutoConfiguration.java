package io.ddd4j.boot.cmpt.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.boot.cmpt.crypto.provider.CryptoProvider;
import io.ddd4j.boot.cmpt.crypto.provider.DefaultCryptoProvider;
import io.ddd4j.boot.cmpt.crypto.strategy.CryptoStrategy;
import io.ddd4j.boot.cmpt.crypto.strategy.DefaultCryptoStrategy;
import io.ddd4j.boot.cmpt.crypto.strategy.FlksecCryptoStrategy;
import io.ddd4j.boot.cmpt.crypto.strategy.NoOpCryptoStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.stream.Collectors;

/**
 * @author wandl
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ObjectMapper.class})
@EnableConfigurationProperties(CryptoProperties.class)
public class DefaultCryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CryptoProvider.class)
    public DefaultCryptoProvider cryptoProvider(ObjectProvider<CryptoStrategy> cryptoStrategyProvider, CryptoProperties cryptoProperties) {
        return new DefaultCryptoProvider(cryptoStrategyProvider.stream().collect(Collectors.toList()), cryptoProperties);
    }

    @Bean
    @ConditionalOnMissingBean(NoOpCryptoStrategy.class)
    public NoOpCryptoStrategy noOpCryptoStrategy(ObjectProvider<ObjectMapper> objectMapperProvider, CryptoProperties cryptoProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new NoOpCryptoStrategy(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(DefaultCryptoStrategy.class)
    public DefaultCryptoStrategy defaultCryptoStrategy(ObjectProvider<ObjectMapper> objectMapperProvider, CryptoProperties cryptoProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new DefaultCryptoStrategy(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(FlksecCryptoStrategy.class)
    public FlksecCryptoStrategy flksecCryptoStrategy(ObjectProvider<ObjectMapper> objectMapperProvider,
                                                     ObjectProvider<RestClient> restClientObjectProvider,
                                                     CryptoProperties cryptoProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        RestClient restClient = restClientObjectProvider.getIfAvailable();
        return new FlksecCryptoStrategy(objectMapper, restClient, cryptoProperties.getFlksecAddress(), cryptoProperties.getFlksecPort());
    }

}
