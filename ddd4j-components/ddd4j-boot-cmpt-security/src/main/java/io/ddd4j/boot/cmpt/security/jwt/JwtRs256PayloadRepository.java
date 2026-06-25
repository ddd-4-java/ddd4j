/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.cmpt.security.jwt;

import com.github.hiwepy.jwt.token.SignedWithSecretKeyJWTRepository;
import com.github.hiwepy.jwt.utils.SecretKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.RedisOperationTemplate;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

@Slf4j
public class JwtRs256PayloadRepository extends AbstractJwtPayloadRepository implements InitializingBean {

    private PublicKey publicKey;
    private PrivateKey privateKey;

    public JwtRs256PayloadRepository(SignedWithSecretKeyJWTRepository secretKeyJWTRepository,
                                     RedisOperationTemplate redisOperationTemplate,
                                     JwtIssueProperteis jwtIssueProperteis) {
        super(secretKeyJWTRepository, redisOperationTemplate, jwtIssueProperteis);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 1、确保AES加解密器已经初始化
        AesBytesEncryptor aesBytesEncryptor = new AesBytesEncryptor(getJwtIssueProperteis().getAecKey(), getJwtIssueProperteis().getAecIv());
        // 2、从配置文件中获取公钥
        byte[] publicKeyBytes = Base64.getDecoder().decode(getJwtIssueProperteis().getRsaPubKey());
        this.publicKey = SecretKeyUtils.genPublicKey(SecretKeyUtils.KEY_RSA, publicKeyBytes);
        log.debug("RSA公钥 - Base64: {}" , this.publicKey);
        // 3、从配置文件中获取私钥
        byte[] privateKeyBytes = Base64.getDecoder().decode(getJwtIssueProperteis().getRsaPriKey());
        String pri_key = Base64.getEncoder().encodeToString(aesBytesEncryptor.decrypt(privateKeyBytes));
        // 3、从RSA私钥原文生成私钥对象
        this.privateKey = SecretKeyUtils.genPrivateKey(SecretKeyUtils.KEY_RSA, aesBytesEncryptor.decrypt(privateKeyBytes));
        log.debug("RSA私钥 - Base64: {}" , this.privateKey );
    }

    @Override
    public Key getSigningKey() {
        return privateKey;
    }

    @Override
    public Key getVerificationKey() {
        return publicKey;
    }

}
