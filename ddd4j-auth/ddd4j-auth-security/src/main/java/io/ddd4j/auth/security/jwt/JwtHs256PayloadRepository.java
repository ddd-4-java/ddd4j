/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.auth.security.jwt;

import com.github.hiwepy.jwt.token.SignedWithSecretKeyJWTRepository;
import com.github.hiwepy.jwt.utils.SecretKeyUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.RedisOperationTemplate;

import java.security.Key;
import java.util.Base64;

public class JwtHs256PayloadRepository extends AbstractJwtPayloadRepository implements InitializingBean {

    private Key secretKey;

    public JwtHs256PayloadRepository(SignedWithSecretKeyJWTRepository secretKeyJWTRepository,
                                     RedisOperationTemplate redisOperationTemplate,
                                     JwtIssueProperteis jwtIssueProperteis) {
        super(secretKeyJWTRepository, redisOperationTemplate, jwtIssueProperteis);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 1、从配置文件中获取HMAC密钥
        byte[] hmacKey = Base64.getDecoder().decode(getJwtIssueProperteis().getHmacKey());
        // 2、从HMAC密钥原文生成HMAC密钥对象
        this.secretKey = SecretKeyUtils.genSecretKey(hmacKey, "HmacSHA256");
    }

    @Override
    public Key getSigningKey() {
        return secretKey;
    }

    @Override
    public Key getVerificationKey() {
        return secretKey;
    }

}
