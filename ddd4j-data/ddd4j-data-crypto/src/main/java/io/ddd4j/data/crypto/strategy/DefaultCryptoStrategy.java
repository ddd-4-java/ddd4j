package io.ddd4j.data.crypto.strategy;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.data.crypto.domain.enums.CryptoType;
import io.ddd4j.data.crypto.domain.enums.SymmetricAlgorithmType;
import io.ddd4j.data.crypto.util.SymmetricCryptoUtil;
import io.ddd4j.core.ApiCode;
import io.ddd4j.core.exception.BizRuntimeException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 请求加解密内部服务实现
 * 传输机密性：SM4（国密对称加密算法）
 * 传输完整性：SM3（国密摘要签名算法）
 */
@Slf4j
public class DefaultCryptoStrategy implements CryptoStrategy {

    @Getter
    private ObjectMapper objectMapper;

    public DefaultCryptoStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CryptoType getType() {
        return CryptoType.INTERNAL;
    }

    @Override
    public <T> String encrypt(T value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode) {
        try {
            // 1、序列化Value
            String valueAsString = getObjectMapper().writeValueAsString(value);
            log.debug("Plain Value To {} Encrypt: {}", algorithmType.getName(), valueAsString);
            // 2、获取加密器
            SymmetricCrypto crypto = SymmetricCryptoUtil.getSymmetricCrypto(algorithmType.getName(), encMode, padMode, Base64.decodeStr(key), Objects.isNull(iv) ? null : Base64.decodeStr(iv));
            // 3、加密Value，如果 plainIsEncode =true 则对加密结果进行Base64
            if (plainIsEncode) {
                valueAsString = crypto.encryptBase64(valueAsString);
            } else {
                valueAsString = new String(crypto.encrypt(valueAsString), StandardCharsets.UTF_8);
            }
            log.debug("{} Encrypt Value : {}", algorithmType.getName(), valueAsString);
            return valueAsString;
        } catch (Exception ex) {
            log.error("{} Encrypt Error : {}", algorithmType.getName(), ex.getMessage());
            throw new BizRuntimeException(ApiCode.SC_INTERNAL_SERVER_ERROR, algorithmType.getName() + " Encrypt Error");
        }
    }

    @Override
    public <T> T decrypt(String value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode, Class<T> rtType) {
        try {
            log.debug("Plain Value to {} Decrypt : {}", algorithmType.getName(), value);
            // 1、获取解密器
            SymmetricCrypto crypto = SymmetricCryptoUtil.getSymmetricCrypto(algorithmType.getName(), encMode, padMode, Base64.decodeStr(key), Objects.isNull(iv) ? null : Base64.decodeStr(iv));
            // 2、解密请求体
            String decryptStr = crypto.decryptStr(value);
            log.debug("{} Decrypt Value : {}", algorithmType.getName(), decryptStr);
            return getObjectMapper().readValue(decryptStr, rtType);
        } catch (Exception ex) {
            log.error("{} Decrypt Error : {}", algorithmType.getName(), ex.getMessage());
            throw new BizRuntimeException(ApiCode.SC_INTERNAL_SERVER_ERROR, algorithmType.getName() + " Decrypt Error");
        }
    }

    @Override
    public <T> String hmac(T value, HmacAlgorithm hmacAlgorithm, String key, String iv, boolean plainIsEncode) {
        try {
            log.debug("Plain Value to {} HMAC : {}", hmacAlgorithm.name(), value);
            HMac hMac = SymmetricCryptoUtil.getHmac(hmacAlgorithm, Base64.decodeStr(key));
            String hmacValue;
            if (plainIsEncode) {
                hmacValue = hMac.digestBase64(getObjectMapper().writeValueAsString(value), StandardCharsets.UTF_8, Boolean.TRUE);
            } else {
                hmacValue = new String(hMac.digest(getObjectMapper().writeValueAsString(value)), StandardCharsets.UTF_8);
            }
            log.debug("HMAC Digest Value : {}", hmacValue);
            return hmacValue;
        } catch (Exception ex) {
            log.error("HMAC Digest Error : {}", ex.getMessage());
            throw new BizRuntimeException(ApiCode.SC_INTERNAL_SERVER_ERROR, "HMAC Digest Error");
        }
    }

}
