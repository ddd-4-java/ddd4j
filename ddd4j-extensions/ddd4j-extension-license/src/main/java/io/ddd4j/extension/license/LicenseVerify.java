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
package io.ddd4j.extension.license;

import io.ddd4j.extension.license.manager.CustomLicenseManager;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Objects;

/**
 * License 校验端。
 *
 * <p>安装时使用 JDK KeyStore 中的公钥验证签名，并缓存已验证的许可证内容。
 * 缓存命中仍会检查有效期和机器约束，避免把缓存当成授权旁路。</p>
 */
@Slf4j
public class LicenseVerify {

    /**
     * 当前服务接受的许可证主题。
     */
    private final String subject;
    /**
     * 从验证密钥库中选择公钥证书条目的别名。
     */
    private final String publicAlias;
    /**
     * 打开验证密钥库的口令。
     */
    private final String storePass;
    /**
     * 当前待安装和验证的许可证文件路径。
     */
    private final String licensePath;
    /**
     * 保存验证公钥的密钥库文件路径。
     */
    private final String publicKeysStorePath;
    /**
     * 执行许可证约束验证的管理组件。
     */
    private final CustomLicenseManager licenseManager;
    /**
     * 用于关联当前许可证和公钥配置的验证缓存键。
     */
    private final String cacheKey;

    /**
     * 最近一次安装及验签是否成功。
     */
    private volatile boolean installSuccess;
    /**
     * 许可证验证结果的缓存存活秒数。
     */
    private long cacheTtlSeconds = LicenseCache.DEFAULT_TTL_SECONDS;

    /**
     * 创建许可证校验器。
     *
     * @param subject             许可证 subject
     * @param publicAlias         公钥别称
     * @param storePass           公钥库密码
     * @param licensePath         许可证文件路径
     * @param publicKeysStorePath 公钥库路径
     */
    public LicenseVerify(String subject, String publicAlias, String storePass, String licensePath, String publicKeysStorePath) {
        requireText(subject, "subject");
        requireText(publicAlias, "publicAlias");
        requireText(storePass, "storePass");
        requireText(licensePath, "licensePath");
        requireText(publicKeysStorePath, "publicKeysStorePath");
        this.subject = subject;
        this.publicAlias = publicAlias;
        this.storePass = storePass;
        this.licensePath = licensePath;
        this.publicKeysStorePath = publicKeysStorePath;
        this.licenseManager = new CustomLicenseManager();
        this.cacheKey = buildCacheKey();
    }

    /**
     * 检查许可证配置中的必填文本。
     *
     * @param value 待检查的配置值
     * @param field 用于诊断的配置字段名称
     * @throws IllegalArgumentException 配置未提供有效文本
     */
    private static void requireText(String value, String field) {
        if (StrKit.isBlank(value)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    /**
     * 设置验证结果缓存 TTL。
     *
     * @param cacheTtlSeconds TTL 秒数；非正数时使用默认值
     */
    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds > 0 ? cacheTtlSeconds : LicenseCache.DEFAULT_TTL_SECONDS;
    }

    /**
     * @return 是否安装成功
     */
    public boolean isInstallSuccess() {
        return installSuccess;
    }

    /**
     * 验证签名并安装许可证。
     */
    public synchronized void installLicense() {
        try {
            LicenseCache.init(cacheTtlSeconds);
            LicenseInfo licenseInfo = readVerifiedLicense();
            validateLicense(licenseInfo);
            LicenseCache.put(cacheKey, licenseInfo);
            installSuccess = true;
            log.info("License 证书安装成功: subject={}, notBefore={}, notAfter={}",
                    subject, licenseInfo.getNotBefore(), licenseInfo.getNotAfter());
        } catch (Exception exception) {
            installSuccess = false;
            LicenseCache.invalidate(cacheKey);
            log.warn("License 安装失败: subject={}, path={}, msg={}", subject, licensePath, exception.getMessage());
        }
    }

    /**
     * 卸载许可证并移除缓存。
     */
    public synchronized void unInstallLicense() {
        installSuccess = false;
        LicenseCache.invalidate(cacheKey);
    }

    /**
     * 校验证书。
     *
     * @return 校验通过返回 {@code true}
     */
    public boolean verify() {
        return verifyResult().isValid();
    }

    /**
     * 校验证书并返回结构化结果。
     *
     * @return 校验结果
     */
    public synchronized LicenseVerificationResult verifyResult() {
        if (!installSuccess) {
            return LicenseVerificationResult.failure(LicenseVerificationResult.Status.NOT_INSTALLED, "证书未安装成功");
        }
        LicenseInfo cached = LicenseCache.get(cacheKey);
        if (Objects.nonNull(cached)) {
            try {
                validateLicense(cached);
                return LicenseVerificationResult.success(cached);
            } catch (IllegalStateException exception) {
                LicenseCache.invalidate(cacheKey);
                return LicenseVerificationResult.failure(statusFor(exception), exception.getMessage());
            }
        }
        try {
            LicenseInfo licenseInfo = readVerifiedLicense();
            validateLicense(licenseInfo);
            LicenseCache.put(cacheKey, licenseInfo);
            return LicenseVerificationResult.success(licenseInfo);
        } catch (Exception exception) {
            LicenseCache.invalidate(cacheKey);
            return LicenseVerificationResult.failure(statusFor(exception), exception.getMessage());
        }
    }

    /**
     * 返回最近一次缓存中的许可证信息。
     *
     * @return 许可证信息；没有缓存时返回 {@code null}
     */
    public LicenseInfo getLicenseInfo() {
        return LicenseCache.get(cacheKey);
    }

    /**
     * @return 是否已有缓存结果
     */
    public boolean isCached() {
        return LicenseCache.exists(cacheKey);
    }

    /**
     * 读取签名信封并用配置公钥验签，通过后解析许可证信息。
     *
     * @return 通过签名验证的许可证信息
     * @throws Exception 文件、密钥、公钥验证或负载解析失败
     */
    private LicenseInfo readVerifiedLicense() throws Exception {
        SignedLicense signedLicense = SignedLicense.parse(Files.readAllBytes(Paths.get(licensePath)));
        PublicKey publicKey = loadPublicKey();
        Signature signature = Signature.getInstance(signedLicense.algorithm());
        signature.initVerify(publicKey);
        signature.update(signedLicense.payload());
        if (!signature.verify(signedLicense.signature())) {
            throw new IllegalStateException("License 签名校验失败");
        }
        LicenseInfo licenseInfo = JsonKit.toObject(new String(signedLicense.payload(), java.nio.charset.StandardCharsets.UTF_8), LicenseInfo.class);
        if (Objects.isNull(licenseInfo)) {
            throw new IllegalStateException("License 内容格式无效");
        }
        return licenseInfo;
    }

    /**
     * 打开配置的 JKS 密钥库并读取指定证书的公钥。
     *
     * @return 许可证验签所用的公钥
     * @throws Exception 无法读取密钥库或找不到指定证书
     */
    private PublicKey loadPublicKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (var inputStream = Files.newInputStream(Paths.get(publicKeysStorePath))) {
            keyStore.load(inputStream, storePass.toCharArray());
        }
        var certificate = keyStore.getCertificate(publicAlias);
        if (Objects.isNull(certificate)) {
            throw new IllegalStateException("公钥别称不存在: " + publicAlias);
        }
        return certificate.getPublicKey();
    }

    /**
     * 核对主题、有效期和当前环境绑定要求。
     *
     * @param licenseInfo 已经解析的许可证信息
     */
    private void validateLicense(LicenseInfo licenseInfo) {
        if (!Objects.equals(subject, licenseInfo.getSubject())) {
            throw new IllegalStateException("License subject 不匹配");
        }
        if (!licenseInfo.isValidNow()) {
            throw new IllegalStateException("License 不在有效期内");
        }
        licenseManager.validate(licenseInfo);
    }

    /**
     * 将环境绑定失败与其他许可证验证故障分类。
     *
     * @param exception 验证过程中抛出的异常
     * @return 对外报告的失败类型
     */
    private LicenseVerificationResult.Status statusFor(Exception exception) {
        String message = exception.getMessage();
        if (Objects.nonNull(message) && (message.contains("IP 校验") || message.contains("MAC 校验") || message.contains("SN 校验"))) {
            return LicenseVerificationResult.Status.ENVIRONMENT_MISMATCH;
        }
        return LicenseVerificationResult.Status.INVALID;
    }

    /**
     * 根据主题、公钥别名及规范化文件路径生成验证缓存键。
     *
     * @return 区分当前许可证与验证配置的缓存键
     */
    private String buildCacheKey() {
        String normalizedLicensePath = Paths.get(licensePath).toAbsolutePath().normalize().toString();
        String normalizedKeyStorePath = Paths.get(publicKeysStorePath).toAbsolutePath().normalize().toString();
        return subject + ":" + Integer.toHexString(Objects.hash(publicAlias, normalizedLicensePath, normalizedKeyStorePath));
    }

}
