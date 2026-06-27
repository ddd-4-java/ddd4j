/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.auth.security;

import com.github.hiwepy.jwt.time.JwtTimeProvider;
import com.github.hiwepy.jwt.token.SignedWithSecretKeyJWTRepository;
import com.github.hiwepy.jwt.token.SignedWithSecretResolverJWTRepository;
import io.ddd4j.auth.security.jwt.*;
import io.jsonwebtoken.JwtClock;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisOperationTemplate;
import org.springframework.security.boot.biz.userdetails.JwtPayloadRepository;

@Configuration
public class WebSecurityJwtConfiguration {

    @Bean
    public SigningKeyResolver signingKeyResolver(RedisOperationTemplate redisOperationTemplate) {
        return new JwtSigningKeyRedisResolver(redisOperationTemplate);
    }

    @Bean
    public SignedWithSecretResolverJWTRepository secretResolverJWTRepository(SigningKeyResolver signingKeyResolver) {
        return new SignedWithSecretResolverJWTRepository(signingKeyResolver);
    }

    @Bean
    public JwtTimeProvider jwtTimeProvider(RedisOperationTemplate redisOperation) {
        return new JwtTimeRedisProvider(redisOperation);
    }

    @Bean
    public JwtClock jwtClock(JwtTimeProvider timeProvider) {
        JwtClock clock = new JwtClock();
        clock.setTimeProvider(timeProvider);
        return clock;
    }

    @Bean
    public SignedWithSecretKeyJWTRepository secretKeyJWTRepository(JwtClock jwtClock) {
        SignedWithSecretKeyJWTRepository jWTRepository = new SignedWithSecretKeyJWTRepository();
        jWTRepository.setClock(jwtClock);
        return jWTRepository;
    }

    @Bean
    public JwtPayloadRepository jwtPayloadRepository(SignedWithSecretKeyJWTRepository secretKeyJWTRepository,
                                                     RedisOperationTemplate redisOperationTemplate,
                                                     JwtIssueProperteis jwtIssueProperteis) {
        if (SignatureAlgorithm.HS256.getValue().equalsIgnoreCase(jwtIssueProperteis.getAlgorithm())){
            return new JwtHs256PayloadRepository(secretKeyJWTRepository, redisOperationTemplate, jwtIssueProperteis);
        }
        return new JwtRs256PayloadRepository(secretKeyJWTRepository, redisOperationTemplate, jwtIssueProperteis);
    }

}
