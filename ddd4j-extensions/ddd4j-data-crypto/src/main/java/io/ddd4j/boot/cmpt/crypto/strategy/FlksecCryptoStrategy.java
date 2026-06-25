package io.ddd4j.boot.cmpt.crypto.strategy;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.boot.cmpt.crypto.domain.enums.CryptoType;
import io.ddd4j.boot.cmpt.crypto.domain.enums.SymmetricAlgorithmType;
import io.ddd4j.boot.cmpt.crypto.domain.vo.FlkSecDecryptResponseVO;
import io.ddd4j.boot.cmpt.crypto.domain.vo.FlkSecEncryptResponseVO;
import io.ddd4j.boot.cmpt.crypto.domain.vo.FlkSecSignResponseVO;
import io.ddd4j.boot.core.exception.BizRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 请求加解密服务实现
 */
@Slf4j
public class FlksecCryptoStrategy implements CryptoStrategy {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String address;
    private final String port;

    public FlksecCryptoStrategy(ObjectMapper objectMapper, RestClient restClient, String address, String port) {
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.address = address;
        this.port = port;
    }

    @Override
    public CryptoType getType() {
        return CryptoType.FLKSEC;
    }

    @Override
    public <T> String encrypt(T value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode) {
        try {
            // 1、序列化Value
            String valueAsString = objectMapper.writeValueAsString(value);
            // 2、如果 plainIsEncode =true 则对 valueAsString 进行 Base64 编码
            if (plainIsEncode) {
                valueAsString = Base64.encode(valueAsString);
                log.debug("Base64 Encode String to Encrypt : {}", value);
            }
            Map<String, String> bodyContent = new HashMap<>();
            // 加密的算法类型,目前系统支持 sm1,sm4
            bodyContent.put("algorithmType", "sm4");
            // 采用的加密模式，系统支持 ecb,cbc,cfb,ofb
            bodyContent.put("encMode", encMode);
            // 解密运算所采用的填充模式，系统支持 PKCS5Padding 和 NoPadding
            bodyContent.put("padMode", padMode);
            // Base64 格式的密钥字符串
            bodyContent.put("key", key);
            // Base64 格式的初始向量， 加密模式为 cbc,cfb,ofb 时该参数不能为空，解码后长度为 16 位，可自定义
            bodyContent.put("iv", iv);
            // 需要进行加密的数据
            bodyContent.put("data", valueAsString);
            // 明文是否编码
            bodyContent.put("plainIsEncode", String.valueOf(plainIsEncode));
            // 远程请求地址
            String url = String.format("https://%s:%s/api/crypto/sysEncrypt", address, port);
            ResponseEntity<FlkSecEncryptResponseVO> encryptResponse = restClient.post()
                    .uri(url)
                    .body(bodyContent)
                    .retrieve()
                    .toEntity(FlkSecEncryptResponseVO.class);
            if (encryptResponse.getStatusCode().is2xxSuccessful()) {
                FlkSecEncryptResponseVO encryptResponseVO = encryptResponse.getBody();
                if (Objects.isNull(encryptResponseVO)) {
                    throw new BizRuntimeException("调用远程接口加密失败，请稍后重试");
                }
                if (encryptResponseVO.getCode() == 200) {
                    String responseString = StringUtils.defaultString(encryptResponseVO.getData());
                    log.debug("Response Encrypt Value : {}", responseString);
                    return responseString;
                } else {
                    throw new BizRuntimeException(encryptResponseVO.getMsg());
                }
            } else {
                throw new BizRuntimeException("调用远程接口加密失败，StatusCode :" + encryptResponse.getStatusCode());
            }
        } catch (IOException e) {
            log.error("调用远程接口加密失败：{}", e.getMessage());
            throw new BizRuntimeException("调用远程接口加密失败，请稍后重试");
        }
    }

    @Override
    public <T> T decrypt(String value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode, Class<T> rtType) {
        try {
            Map<String, String> bodyContent = new HashMap<>();
            // 加密的算法类型,目前系统支持 sm1,sm4
            bodyContent.put("algorithmType", "sm4");
            // 采用的加密模式，系统支持 ecb,cbc,cfb,ofb
            bodyContent.put("encMode", encMode);
            // 解密运算所采用的填充模式，系统支持 PKCS5Padding 和 NoPadding
            bodyContent.put("padMode", padMode);
            // Base64 格式的密钥字符串
            bodyContent.put("key", key);
            // Base64 格式的初始向量， 加密模式为 cbc,cfb,ofb 时该参数不能为空，解码后长度为 16 位，可自定义
            bodyContent.put("iv", iv);
            // 需要进行解密的数据
            bodyContent.put("data", value);
            // 明文是否编码
            bodyContent.put("plainIsEncode", String.valueOf(plainIsEncode));
            // 远程请求地址
            String url = String.format("https://%s:%s/api/crypto/sysDecrypt", address, port);
            ResponseEntity<FlkSecDecryptResponseVO> decryptResponse = restClient.post()
                    .uri(url)
                    .body(bodyContent)
                    .retrieve()
                    .toEntity(FlkSecDecryptResponseVO.class);
            if (decryptResponse.getStatusCode().is2xxSuccessful()) {
                FlkSecDecryptResponseVO decryptResponseVO = decryptResponse.getBody();
                if (Objects.isNull(decryptResponseVO)) {
                    throw new BizRuntimeException("调用远程接口解密失败，请稍后重试");
                }
                if (decryptResponseVO.getCode() == 200) {
                    String responseString = StringUtils.defaultString(decryptResponseVO.getData());
                    log.debug("Response Decrypt Value : {}", responseString);
                    return objectMapper.readValue(value, rtType);
                } else {
                    throw new BizRuntimeException(decryptResponseVO.getMsg());
                }
            } else {
                throw new BizRuntimeException("调用远程接口解密失败，StatusCode :" + decryptResponse.getStatusCode());
            }
        } catch (IOException e) {
            log.error("调用远程接口解密失败：{}", e.getMessage());
            throw new BizRuntimeException("调用远程接口解密失败，请稍后重试");
        }
    }

    @Override
    public <T> String hmac(T value, HmacAlgorithm hmacAlgorithm, String key, String iv, boolean plainIsEncode) {
        try {
            // 1、序列化Value
            String valueAsString = objectMapper.writeValueAsString(value);
            // 2、如果 plainIsEncode =true 则对 valueAsString 进行 Base64 编码
            if (plainIsEncode) {
                valueAsString = Base64.encode(valueAsString);
                log.debug("Base64 Encode String to Hmac : {}", value);
            }
            Map<String, String> bodyContent = new HashMap<>();
            // 明文是否编码
            bodyContent.put("plainIsEncode", String.valueOf(plainIsEncode));
            // Base64 格式的密钥字符串
            bodyContent.put("key", key);
            // 进行杂凑的数据，数据大小建议不要超过 100M，比较大的数据可以每 100M分块计算，最后进行比较
            bodyContent.put("data", valueAsString);
            // 远程请求地址
            String url = String.format("https://%s:%s/api/hmac/sm3hmac", address, port);
            ResponseEntity<FlkSecSignResponseVO> response = restClient.post()
                    .uri(url)
                    .body(bodyContent)
                    .retrieve()
                    .toEntity(FlkSecSignResponseVO.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                FlkSecSignResponseVO responseVO = response.getBody();
                if (Objects.isNull(responseVO)) {
                    throw new BizRuntimeException("调用远程接口签名失败，请稍后重试");
                }
                if (responseVO.getCode() == 200) {
                    return StringUtils.defaultString(responseVO.getData());
                } else {
                    throw new BizRuntimeException(responseVO.getMsg());
                }
            } else {
                throw new BizRuntimeException("调用远程接口签名失败，StatusCode :" + response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("调用远程接口签名失败：{}", e.getMessage());
            throw new BizRuntimeException("调用远程接口签名失败，请稍后重试");
        }
    }

}
