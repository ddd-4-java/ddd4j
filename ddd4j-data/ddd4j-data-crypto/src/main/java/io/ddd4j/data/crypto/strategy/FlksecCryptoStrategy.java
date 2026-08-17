package io.ddd4j.data.crypto.strategy;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.data.crypto.enums.CryptoType;
import io.ddd4j.data.crypto.enums.SymmetricAlgorithmType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 请求加解密服务实现
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class FlksecCryptoStrategy implements CryptoStrategy {

    /**
     * JSON 对象映射器
     */
    private final ObjectMapper objectMapper;
    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;
    /**
     * 远程服务地址
     */
    private final String address;
    /**
     * 远程服务端口
     */
    private final String port;

    /**
     * 构造函数
     *
     * @param objectMapper JSON 对象映射器
     * @param httpClient   HTTP 客户端
     * @param address      远程服务地址
     * @param port         远程服务端口
     */
    public FlksecCryptoStrategy(ObjectMapper objectMapper, HttpClient httpClient, String address, String port) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.address = address;
        this.port = port;
    }

    /**
     * 获取加解密方式
     *
     * @return 弗兰科信息加解密方式
     */
    @Override
    public CryptoType getType() {
        return CryptoType.FLKSEC;
    }

    /**
     * 字段加密（远程调用弗兰科信息加密服务）
     */
    @Override
    public <T> String encrypt(T value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode) {
        try {
            String valueAsString = objectMapper.writeValueAsString(value);
            if (plainIsEncode) {
                valueAsString = Base64.encode(valueAsString);
                log.debug("Base64 Encode String to Encrypt : {}", value);
            }
            Map<String, String> bodyContent = new HashMap<>();
            bodyContent.put("algorithmType", "sm4");
            bodyContent.put("encMode", encMode);
            bodyContent.put("padMode", padMode);
            bodyContent.put("key", key);
            bodyContent.put("iv", iv);
            bodyContent.put("data", valueAsString);
            bodyContent.put("plainIsEncode", String.valueOf(plainIsEncode));
            String url = String.format("https://%s:%s/api/crypto/sysEncrypt", address, port);
            HttpResponse<String> encryptResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyContent)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (encryptResponse.statusCode() >= 200 && encryptResponse.statusCode() < 300) {
                EncryptResponse encryptResponseVO = objectMapper.readValue(encryptResponse.body(), EncryptResponse.class);
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
                throw new BizRuntimeException("调用远程接口加密失败，StatusCode :" + encryptResponse.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("调用远程接口加密失败：{}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BizRuntimeException("调用远程接口加密失败，请稍后重试");
        }
    }

    /**
     * 字段解密（远程调用弗兰科信息解密服务）
     */
    @Override
    public <T> T decrypt(String value, SymmetricAlgorithmType algorithmType, String encMode, String padMode, String key, String iv, boolean plainIsEncode, Class<T> rtType) {
        try {
            Map<String, String> bodyContent = new HashMap<>();
            bodyContent.put("algorithmType", "sm4");
            bodyContent.put("encMode", encMode);
            bodyContent.put("padMode", padMode);
            bodyContent.put("key", key);
            bodyContent.put("iv", iv);
            bodyContent.put("data", value);
            bodyContent.put("plainIsEncode", String.valueOf(plainIsEncode));
            String url = String.format("https://%s:%s/api/crypto/sysDecrypt", address, port);
            HttpResponse<String> decryptResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyContent)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (decryptResponse.statusCode() >= 200 && decryptResponse.statusCode() < 300) {
                DecryptResponse decryptResponseVO = objectMapper.readValue(decryptResponse.body(), DecryptResponse.class);
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
                throw new BizRuntimeException("调用远程接口解密失败，StatusCode :" + decryptResponse.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("调用远程接口解密失败：{}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BizRuntimeException("调用远程接口解密失败，请稍后重试");
        }
    }

    /**
     * HMAC 签名（远程调用弗兰科信息签名服务）
     */
    @Override
    public <T> String hmac(T value, HmacAlgorithm hmacAlgorithm, String key, String iv, boolean plainIsEncode) {
        try {
            String valueAsString = objectMapper.writeValueAsString(value);
            if (plainIsEncode) {
                valueAsString = Base64.encode(valueAsString);
                log.debug("Base64 Encode String to Hmac : {}", value);
            }
            Map<String, String> bodyContent = new HashMap<>();
            bodyContent.put("plainIsEncode", String.valueOf(plainIsEncode));
            bodyContent.put("key", key);
            bodyContent.put("data", valueAsString);
            String url = String.format("https://%s:%s/api/hmac/sm3hmac", address, port);
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyContent)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                SignResponse responseVO = objectMapper.readValue(response.body(), SignResponse.class);
                if (Objects.isNull(responseVO)) {
                    throw new BizRuntimeException("调用远程接口签名失败，请稍后重试");
                }
                if (responseVO.getCode() == 200) {
                    return StringUtils.defaultString(responseVO.getData());
                } else {
                    throw new BizRuntimeException(responseVO.getMsg());
                }
            } else {
                throw new BizRuntimeException("调用远程接口签名失败，StatusCode :" + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("调用远程接口签名失败：{}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BizRuntimeException("调用远程接口签名失败，请稍后重试");
        }
    }

    // ======================== 内部响应 VO ========================

    /**
     * 弗兰科信息加密响应
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EncryptResponse {

        /**
         * 200:成功
         */
        @JsonProperty("code")
        private int code;

        /**
         * 成功或失败的提示信息
         */
        @JsonProperty("msg")
        private String msg;

        /**
         * 分段加密时使用
         */
        @JsonProperty("iv")
        private String iv;

        /**
         * 加密后的数据
         */
        @JsonProperty("data")
        private String data;
    }

    /**
     * 弗兰科信息解密响应
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DecryptResponse {

        /**
         * 200:成功
         */
        @JsonProperty("code")
        private int code;

        /**
         * 成功或失败的提示信息
         */
        @JsonProperty("msg")
        private String msg;

        /**
         * 分段加密时使用
         */
        @JsonProperty("iv")
        private String iv;

        /**
         * 解密后的数据
         */
        @JsonProperty("data")
        private String data;
    }

    /**
     * 弗兰科信息签名响应
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SignResponse {

        /**
         * 200:成功
         */
        @JsonProperty("code")
        private int code;

        /**
         * 成功或失败的提示信息
         */
        @JsonProperty("msg")
        private String msg;

        /**
         * 签名后的数据
         */
        @JsonProperty("data")
        private String data;
    }

}
