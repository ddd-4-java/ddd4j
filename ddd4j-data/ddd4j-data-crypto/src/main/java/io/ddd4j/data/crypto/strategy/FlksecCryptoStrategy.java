package io.ddd4j.data.crypto.strategy;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.data.crypto.domain.enums.CryptoType;
import io.ddd4j.data.crypto.domain.enums.SymmetricAlgorithmType;
import io.ddd4j.data.crypto.domain.vo.FlkSecDecryptResponseVO;
import io.ddd4j.data.crypto.domain.vo.FlkSecEncryptResponseVO;
import io.ddd4j.data.crypto.domain.vo.FlkSecSignResponseVO;
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

    /** JSON 对象映射器 */
    private final ObjectMapper objectMapper;
    /** HTTP 客户端 */
    private final HttpClient httpClient;
    /** 远程服务地址 */
    private final String address;
    /** 远程服务端口 */
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
     *
     * @param value         待加密字段的值
     * @param algorithmType 加密算法类型
     * @param encMode       采用的加密模式
     * @param padMode       填充模式
     * @param key           Base64 格式的密钥字符串
     * @param iv            Base64 格式的初始向量
     * @param plainIsEncode 明文是否进行了 base64 编码
     * @param <T>           字段类型
     * @return 加密后的字符串
     */
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
            HttpResponse<String> encryptResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyContent)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (encryptResponse.statusCode() >= 200 && encryptResponse.statusCode() < 300) {
                FlkSecEncryptResponseVO encryptResponseVO = objectMapper.readValue(encryptResponse.body(), FlkSecEncryptResponseVO.class);
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
     *
     * @param value         待解密字段的值
     * @param algorithmType 加密算法类型
     * @param encMode       采用的加密模式
     * @param padMode       填充模式
     * @param key           Base64 格式的密钥字符串
     * @param iv            Base64 格式的初始向量
     * @param plainIsEncode 明文是否进行了 base64 编码
     * @param rtType        返回值类型
     * @param <T>           字段类型
     * @return 解密后的字段值
     */
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
            HttpResponse<String> decryptResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyContent)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (decryptResponse.statusCode() >= 200 && decryptResponse.statusCode() < 300) {
                FlkSecDecryptResponseVO decryptResponseVO = objectMapper.readValue(decryptResponse.body(), FlkSecDecryptResponseVO.class);
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
     *
     * @param value         待签名的值
     * @param hmacAlgorithm HMAC 算法类型
     * @param key           Base64 格式的密钥字符串
     * @param iv            Base64 格式的初始向量
     * @param plainIsEncode 明文是否进行了 base64 编码
     * @param <T>           字段类型
     * @return 签名后的字符串
     */
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
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyContent)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                FlkSecSignResponseVO responseVO = objectMapper.readValue(response.body(), FlkSecSignResponseVO.class);
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

}