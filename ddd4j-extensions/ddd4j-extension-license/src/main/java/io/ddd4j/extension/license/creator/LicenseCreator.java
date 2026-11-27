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

    /**
     * 创建许可证时使用的签发参数。
     */
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
     * 根据签发参数组装将被签名的许可证负载。
     *
     * @return 含有效期和消费约束的许可证信息
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
     * 从签发密钥库加载配置别名下的私钥。
     *
     * @return 用于签名许可证负载的私钥
     * @throws Exception 密钥库读取、口令校验或私钥查找失败
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

    /**
     * 验证许可证签发配置及有效日期等输入约束。
     */
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
     * 检查许可证配置中的必填文本。
     *
     * @param value 待检查的配置值
     * @param field 用于诊断的配置字段名称
     * @throws IllegalStateException 配置未提供有效文本
     */
    private void requireText(String value, String field) {
        if (StrKit.isBlank(value)) {
            throw new IllegalStateException("生成证书缺少必填参数: " + field);
        }
    }

    /**
     * 用生成的临时文件替换许可证目标，文件系统不支持原子移动时执行普通替换。
     *
     * @param source 保存已生成许可证的临时文件
     * @param target 最终许可证文件路径
     * @throws Exception 文件无法移到目标位置
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
     * 为 RSA、EC 或 DSA 私钥选择 SHA-256 签名算法。
     *
     * @param keyAlgorithm 签发私钥的算法标识
     * @return 交给 Java Signature 实例使用的算法名
     * @throws IllegalStateException 私钥算法不受支持
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
