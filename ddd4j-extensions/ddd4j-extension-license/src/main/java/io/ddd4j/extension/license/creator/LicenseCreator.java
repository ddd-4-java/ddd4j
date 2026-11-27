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
package io.ddd4j.extension.license.creator;

import io.ddd4j.extension.license.LicenseInfo;
import io.ddd4j.extension.license.SignedLicense;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Date;
import java.util.Objects;

/**
 * 使用 JDK KeyStore 与签名 API 签发 ddd4j 许可证。
 */
@Slf4j
public class LicenseCreator {

    /** \u521b\u5efa\u8bb8\u53ef\u8bc1\u65f6\u4f7f\u7528\u7684\u7b7e\u53d1\u53c2\u6570\u3002 */
    private final LicenseCreatorParam param;

    /**
     * 创建许可证签发器。
     *
     * @param param 签发参数
     */
    public LicenseCreator(LicenseCreatorParam param) {
        this.param = param;
    }

    /**
     * 生成签名许可证。
     *
     * @return 生成成功返回 {@code true}
     */
    public boolean generateLicense() {
        validateParam();
        if (Objects.isNull(param.getExpiryTime())) {
            param.setExpiryTime(new Date(System.currentTimeMillis() + 365L * 24L * 60L * 60L * 1000L));
            log.warn("未指定 expiryTime，默认设为 1 年后");
        }
        Path target = Paths.get(param.getLicensePath()).toAbsolutePath().normalize();
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            LicenseInfo licenseInfo = createLicenseInfo();
            byte[] payload = JsonKit.toJson(licenseInfo).getBytes(StandardCharsets.UTF_8);
            PrivateKey privateKey = loadPrivateKey();
            String algorithm = signatureAlgorithm(privateKey.getAlgorithm());
            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(privateKey);
            signature.update(payload);
            SignedLicense license = new SignedLicense(algorithm, payload, signature.sign());

            temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.writeString(temporary, license.serialize(), StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
            temporary = null;
            log.info("License 证书生成成功: path={}, subject={}", target, param.getSubject());
            return true;
        } catch (Exception exception) {
            log.error("License 证书生成失败: subject={}, path={}", param.getSubject(), target, exception);
            return false;
        } finally {
            if (Objects.nonNull(temporary)) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception exception) {
                    log.warn("清理 License 临时文件失败: path={}", temporary, exception);
                }
            }
        }
    }

    /**
     * \u6839\u636e\u7b7e\u53d1\u53c2\u6570\u7ec4\u88c5\u5c06\u88ab\u7b7e\u540d\u7684\u8bb8\u53ef\u8bc1\u8d1f\u8f7d\u3002
     * @return \u542b\u6709\u6548\u671f\u548c\u6d88\u8d39\u7ea6\u675f\u7684\u8bb8\u53ef\u8bc1\u4fe1\u606f
     */
    private LicenseInfo createLicenseInfo() {
        return LicenseInfo.builder()
                .subject(param.getSubject())
                .issued(param.getIssuedTime())
                .notBefore(param.getIssuedTime())
                .notAfter(param.getExpiryTime())
                .consumerType(param.getConsumerType())
                .consumerAmount(param.getConsumerAmount())
                .extra(param.getLicenseExtraModel())
                .build();
    }

    /**
     * \u4ece\u7b7e\u53d1\u5bc6\u94a5\u5e93\u52a0\u8f7d\u914d\u7f6e\u522b\u540d\u4e0b\u7684\u79c1\u94a5\u3002
     * @return \u7528\u4e8e\u7b7e\u540d\u8bb8\u53ef\u8bc1\u8d1f\u8f7d\u7684\u79c1\u94a5
     * @throws Exception \u5bc6\u94a5\u5e93\u8bfb\u53d6\u3001\u53e3\u4ee4\u6821\u9a8c\u6216\u79c1\u94a5\u67e5\u627e\u5931\u8d25
     */
    private PrivateKey loadPrivateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (var inputStream = Files.newInputStream(Paths.get(param.getPrivateKeysStorePath()))) {
            keyStore.load(inputStream, param.getStorePass().toCharArray());
        }
        Key key = keyStore.getKey(param.getPrivateAlias(), param.getKeyPass().toCharArray());
        if (!(key instanceof PrivateKey privateKey)) {
            throw new IllegalStateException("私钥别称未指向 PrivateKey: " + param.getPrivateAlias());
        }
        return privateKey;
    }

    /** \u9a8c\u8bc1\u8bb8\u53ef\u8bc1\u7b7e\u53d1\u914d\u7f6e\u53ca\u6709\u6548\u65e5\u671f\u7b49\u8f93\u5165\u7ea6\u675f\u3002 */
    private void validateParam() {
        Objects.requireNonNull(param, "LicenseCreatorParam 不能为空");
        requireText(param.getSubject(), "subject");
        requireText(param.getPrivateAlias(), "privateAlias");
        requireText(param.getKeyPass(), "keyPass");
        requireText(param.getStorePass(), "storePass");
        requireText(param.getPrivateKeysStorePath(), "privateKeysStorePath");
        requireText(param.getLicensePath(), "licensePath");
        requireText(param.getConsumerType(), "consumerType");
        if (!Files.isReadable(Paths.get(param.getPrivateKeysStorePath()))) {
            throw new IllegalStateException("私钥库不存在或不可读: " + param.getPrivateKeysStorePath());
        }
        if (Objects.isNull(param.getConsumerAmount()) || param.getConsumerAmount() <= 0) {
            throw new IllegalStateException("consumerAmount 必须大于 0");
        }
        if (Objects.nonNull(param.getExpiryTime()) && Objects.nonNull(param.getIssuedTime())
                && !param.getExpiryTime().after(param.getIssuedTime())) {
            throw new IllegalStateException("expiryTime 必须晚于 issuedTime");
        }
    }

    /**
     * \u68c0\u67e5\u8bb8\u53ef\u8bc1\u914d\u7f6e\u4e2d\u7684\u5fc5\u586b\u6587\u672c\u3002
     * @param value \u5f85\u68c0\u67e5\u7684\u914d\u7f6e\u503c
     * @param field \u7528\u4e8e\u8bca\u65ad\u7684\u914d\u7f6e\u5b57\u6bb5\u540d\u79f0
     * @throws IllegalStateException \u914d\u7f6e\u672a\u63d0\u4f9b\u6709\u6548\u6587\u672c
     */
    private void requireText(String value, String field) {
        if (StrKit.isBlank(value)) {
            throw new IllegalStateException("生成证书缺少必填参数: " + field);
        }
    }

    /**
     * \u7528\u751f\u6210\u7684\u4e34\u65f6\u6587\u4ef6\u66ff\u6362\u8bb8\u53ef\u8bc1\u76ee\u6807\uff0c\u6587\u4ef6\u7cfb\u7edf\u4e0d\u652f\u6301\u539f\u5b50\u79fb\u52a8\u65f6\u6267\u884c\u666e\u901a\u66ff\u6362\u3002
     * @param source \u4fdd\u5b58\u5df2\u751f\u6210\u8bb8\u53ef\u8bc1\u7684\u4e34\u65f6\u6587\u4ef6
     * @param target \u6700\u7ec8\u8bb8\u53ef\u8bc1\u6587\u4ef6\u8def\u5f84
     * @throws Exception \u6587\u4ef6\u65e0\u6cd5\u79fb\u5230\u76ee\u6807\u4f4d\u7f6e
     */
    private void moveAtomically(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * \u4e3a RSA\u3001EC \u6216 DSA \u79c1\u94a5\u9009\u62e9 SHA-256 \u7b7e\u540d\u7b97\u6cd5\u3002
     * @param keyAlgorithm \u7b7e\u53d1\u79c1\u94a5\u7684\u7b97\u6cd5\u6807\u8bc6
     * @return \u4ea4\u7ed9 Java Signature \u5b9e\u4f8b\u4f7f\u7528\u7684\u7b97\u6cd5\u540d
     * @throws IllegalStateException \u79c1\u94a5\u7b97\u6cd5\u4e0d\u53d7\u652f\u6301
     */
    private String signatureAlgorithm(String keyAlgorithm) {
        if ("EC".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withECDSA";
        }
        if ("DSA".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withDSA";
        }
        if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withRSA";
        }
        throw new IllegalStateException("不支持的私钥算法: " + keyAlgorithm);
    }

}
