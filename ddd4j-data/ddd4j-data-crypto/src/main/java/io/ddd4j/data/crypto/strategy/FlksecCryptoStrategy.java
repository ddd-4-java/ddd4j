/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.data.crypto.strategy;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    /** \u8bbf\u95ee FLKSEC \u670d\u52a1\u4f7f\u7528\u7684 HTTP \u6216 HTTPS \u534f\u8bae\u3002 */
    private final String scheme;

    /**
     * 构造函数
     *
     * @param objectMapper JSON 对象映射器
     * @param httpClient   HTTP 客户端
     * @param address      远程服务地址
     * @param port         远程服务端口
     */
    public FlksecCryptoStrategy(ObjectMapper objectMapper, HttpClient httpClient, String address, String port) {
        this(objectMapper, httpClient, address, port, "https");
    }

    /**
     * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>FlksecCryptoStrategy</code> \u5b9e\u4f8b\u3002
     * @param objectMapper JSON \u5bf9\u8c61\u6620\u5c04\u5668
     * @param httpClient HTTP \u5ba2\u6237\u7aef
     * @param address \u8fdc\u7a0b\u670d\u52a1\u5730\u5740
     * @param port \u8fdc\u7a0b\u670d\u52a1\u7aef\u53e3
     * @param scheme <code>scheme</code> \u5b57\u6bb5
     */
    private FlksecCryptoStrategy(ObjectMapper objectMapper, HttpClient httpClient, String address, String port, String scheme) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.address = address;
        this.port = port;
        this.scheme = scheme;
    }

    /**
     * 使用默认 HTTP 客户端连接远程加解密服务。
     *
     * @param objectMapper JSON 对象映射器
     * @param address      远程服务地址
     * @param port         远程服务端口
     */
    public FlksecCryptoStrategy(ObjectMapper objectMapper, String address, String port) {
        this(objectMapper, HttpClient.newHttpClient(), address, port);
    }

    /**
     * \u4ee5\u9ed8\u8ba4 HTTP \u5ba2\u6237\u7aef\u8fde\u63a5\u6307\u5b9a\u7684 FLKSEC \u670d\u52a1\u3002
     * @param objectMapper \u7f16\u7801\u5bc6\u7801\u670d\u52a1\u8bf7\u6c42\u548c\u54cd\u5e94\u7684 JSON \u6620\u5c04\u5668
     * @param address \u5bc6\u7801\u670d\u52a1\u5730\u5740
     * @param port \u5bc6\u7801\u670d\u52a1\u7aef\u53e3
     * @param scheme \u8bf7\u6c42\u534f\u8bae
     */
    FlksecCryptoStrategy(ObjectMapper objectMapper, String address, String port, String scheme) {
        this(objectMapper, HttpClient.newHttpClient(), address, port, scheme);
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
     * \u5b57\u6bb5\u52a0\u5bc6\uff08\u8fdc\u7a0b\u8c03\u7528\u5f17\u5170\u79d1\u4fe1\u606f\u52a0\u5bc6\u670d\u52a1\uff09
     *
     * @param value \u5f85\u52a0\u5bc6\u6570\u636e
     * @param algorithmType \u52a0\u5bc6\u7b97\u6cd5\u7c7b\u578b
     * @param encMode \u5206\u7ec4\u52a0\u5bc6\u5de5\u4f5c\u6a21\u5f0f
     * @param padMode \u5206\u7ec4\u52a0\u5bc6\u586b\u5145\u65b9\u5f0f
     * @param key \u8fd0\u7b97\u6240\u7528\u5bc6\u94a5
     * @param iv \u521d\u59cb\u5316\u5411\u91cf
     * @param plainIsEncode \u660e\u6587\u662f\u5426\u4ee5\u7f16\u7801\u5f62\u5f0f\u4f20\u5165\u6216\u8fd4\u56de
     * @param <T> \u5f85\u52a0\u5bc6\u6570\u636e\u7684\u7c7b\u578b
     * @return \u7f16\u7801\u540e\u7684\u5bc6\u6587
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
            String url = String.format("%s://%s:%s/api/crypto/sysEncrypt", scheme, address, port);
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
     * \u5b57\u6bb5\u89e3\u5bc6\uff08\u8fdc\u7a0b\u8c03\u7528\u5f17\u5170\u79d1\u4fe1\u606f\u89e3\u5bc6\u670d\u52a1\uff09
     *
     * @param value \u5f85\u89e3\u5bc6\u7684\u5bc6\u6587
     * @param algorithmType \u52a0\u5bc6\u7b97\u6cd5\u7c7b\u578b
     * @param encMode \u5206\u7ec4\u52a0\u5bc6\u5de5\u4f5c\u6a21\u5f0f
     * @param padMode \u5206\u7ec4\u52a0\u5bc6\u586b\u5145\u65b9\u5f0f
     * @param key \u8fd0\u7b97\u6240\u7528\u5bc6\u94a5
     * @param iv \u521d\u59cb\u5316\u5411\u91cf
     * @param plainIsEncode \u660e\u6587\u662f\u5426\u4ee5\u7f16\u7801\u5f62\u5f0f\u4f20\u5165\u6216\u8fd4\u56de
     * @param rtType \u89e3\u5bc6\u540e\u8f6c\u6362\u5230\u7684 Java \u7c7b\u578b
     * @param <T> \u89e3\u5bc6\u7ed3\u679c\u7684\u7c7b\u578b
     * @return \u6307\u5b9a\u76ee\u6807\u7c7b\u578b\u7684\u660e\u6587
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
            String url = String.format("%s://%s:%s/api/crypto/sysDecrypt", scheme, address, port);
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
                    return objectMapper.readValue(responseString, rtType);
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
     * HMAC \u7b7e\u540d\uff08\u8fdc\u7a0b\u8c03\u7528\u5f17\u5170\u79d1\u4fe1\u606f\u7b7e\u540d\u670d\u52a1\uff09
     *
     * @param value \u5f85\u8ba1\u7b97\u6d88\u606f\u8ba4\u8bc1\u7801\u7684\u6570\u636e
     * @param hmacAlgorithm \u6d88\u606f\u8ba4\u8bc1\u7801\u7b97\u6cd5
     * @param key \u8fd0\u7b97\u6240\u7528\u5bc6\u94a5
     * @param iv \u521d\u59cb\u5316\u5411\u91cf
     * @param plainIsEncode \u660e\u6587\u662f\u5426\u4ee5\u7f16\u7801\u5f62\u5f0f\u4f20\u5165\u6216\u8fd4\u56de
     * @param <T> \u5f85\u8ba1\u7b97\u6d88\u606f\u8ba4\u8bc1\u7801\u7684\u6570\u636e\u7c7b\u578b
     * @return \u6d88\u606f\u8ba4\u8bc1\u7801\u7684\u7f16\u7801\u7ed3\u679c
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
            String url = String.format("%s://%s:%s/api/hmac/sm3hmac", scheme, address, port);
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
