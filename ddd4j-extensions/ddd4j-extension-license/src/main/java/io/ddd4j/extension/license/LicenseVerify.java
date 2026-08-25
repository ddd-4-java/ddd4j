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

    private final String subject;
    private final String publicAlias;
    private final String storePass;
    private final String licensePath;
    private final String publicKeysStorePath;
    private final CustomLicenseManager licenseManager;
    private final String cacheKey;

    private volatile boolean installSuccess;
    private long cacheTtlSeconds = LicenseCache.DEFAULT_TTL_SECONDS;

    /**
     * 创建许可证校验器。
     *
     * @param subject 许可证 subject
     * @param publicAlias 公钥别称
     * @param storePass 公钥库密码
     * @param licensePath 许可证文件路径
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

    private void validateLicense(LicenseInfo licenseInfo) {
        if (!Objects.equals(subject, licenseInfo.getSubject())) {
            throw new IllegalStateException("License subject 不匹配");
        }
        if (!licenseInfo.isValidNow()) {
            throw new IllegalStateException("License 不在有效期内");
        }
        licenseManager.validate(licenseInfo);
    }

    private LicenseVerificationResult.Status statusFor(Exception exception) {
        String message = exception.getMessage();
        if (Objects.nonNull(message) && (message.contains("IP 校验") || message.contains("MAC 校验") || message.contains("SN 校验"))) {
            return LicenseVerificationResult.Status.ENVIRONMENT_MISMATCH;
        }
        return LicenseVerificationResult.Status.INVALID;
    }

    private String buildCacheKey() {
        String normalizedLicensePath = Paths.get(licensePath).toAbsolutePath().normalize().toString();
        String normalizedKeyStorePath = Paths.get(publicKeysStorePath).toAbsolutePath().normalize().toString();
        return subject + ":" + Integer.toHexString(Objects.hash(publicAlias, normalizedLicensePath, normalizedKeyStorePath));
    }

    private static void requireText(String value, String field) {
        if (StrKit.isBlank(value)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

}
