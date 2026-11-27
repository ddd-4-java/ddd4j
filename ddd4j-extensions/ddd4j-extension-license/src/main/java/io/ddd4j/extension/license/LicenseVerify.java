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

    /** \u5f53\u524d\u670d\u52a1\u63a5\u53d7\u7684\u8bb8\u53ef\u8bc1\u4e3b\u9898\u3002 */
    private final String subject;
    /** \u4ece\u9a8c\u8bc1\u5bc6\u94a5\u5e93\u4e2d\u9009\u62e9\u516c\u94a5\u8bc1\u4e66\u6761\u76ee\u7684\u522b\u540d\u3002 */
    private final String publicAlias;
    /** \u6253\u5f00\u9a8c\u8bc1\u5bc6\u94a5\u5e93\u7684\u53e3\u4ee4\u3002 */
    private final String storePass;
    /** \u5f53\u524d\u5f85\u5b89\u88c5\u548c\u9a8c\u8bc1\u7684\u8bb8\u53ef\u8bc1\u6587\u4ef6\u8def\u5f84\u3002 */
    private final String licensePath;
    /** \u4fdd\u5b58\u9a8c\u8bc1\u516c\u94a5\u7684\u5bc6\u94a5\u5e93\u6587\u4ef6\u8def\u5f84\u3002 */
    private final String publicKeysStorePath;
    /** \u6267\u884c\u8bb8\u53ef\u8bc1\u7ea6\u675f\u9a8c\u8bc1\u7684\u7ba1\u7406\u7ec4\u4ef6\u3002 */
    private final CustomLicenseManager licenseManager;
    /** \u7528\u4e8e\u5173\u8054\u5f53\u524d\u8bb8\u53ef\u8bc1\u548c\u516c\u94a5\u914d\u7f6e\u7684\u9a8c\u8bc1\u7f13\u5b58\u952e\u3002 */
    private final String cacheKey;

    /** \u6700\u8fd1\u4e00\u6b21\u5b89\u88c5\u53ca\u9a8c\u7b7e\u662f\u5426\u6210\u529f\u3002 */
    private volatile boolean installSuccess;
    /** \u8bb8\u53ef\u8bc1\u9a8c\u8bc1\u7ed3\u679c\u7684\u7f13\u5b58\u5b58\u6d3b\u79d2\u6570\u3002 */
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
     * \u68c0\u67e5\u8bb8\u53ef\u8bc1\u914d\u7f6e\u4e2d\u7684\u5fc5\u586b\u6587\u672c\u3002
     * @param value \u5f85\u68c0\u67e5\u7684\u914d\u7f6e\u503c
     * @param field \u7528\u4e8e\u8bca\u65ad\u7684\u914d\u7f6e\u5b57\u6bb5\u540d\u79f0
     * @throws IllegalArgumentException \u914d\u7f6e\u672a\u63d0\u4f9b\u6709\u6548\u6587\u672c
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

    /** @return \u662f\u5426\u5b89\u88c5\u6210\u529f */
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

    /** @return \u662f\u5426\u5df2\u6709\u7f13\u5b58\u7ed3\u679c */
    public boolean isCached() {
        return LicenseCache.exists(cacheKey);
    }

    /**
     * \u8bfb\u53d6\u7b7e\u540d\u4fe1\u5c01\u5e76\u7528\u914d\u7f6e\u516c\u94a5\u9a8c\u7b7e\uff0c\u901a\u8fc7\u540e\u89e3\u6790\u8bb8\u53ef\u8bc1\u4fe1\u606f\u3002
     * @return \u901a\u8fc7\u7b7e\u540d\u9a8c\u8bc1\u7684\u8bb8\u53ef\u8bc1\u4fe1\u606f
     * @throws Exception \u6587\u4ef6\u3001\u5bc6\u94a5\u3001\u516c\u94a5\u9a8c\u8bc1\u6216\u8d1f\u8f7d\u89e3\u6790\u5931\u8d25
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
     * \u6253\u5f00\u914d\u7f6e\u7684 JKS \u5bc6\u94a5\u5e93\u5e76\u8bfb\u53d6\u6307\u5b9a\u8bc1\u4e66\u7684\u516c\u94a5\u3002
     * @return \u8bb8\u53ef\u8bc1\u9a8c\u7b7e\u6240\u7528\u7684\u516c\u94a5
     * @throws Exception \u65e0\u6cd5\u8bfb\u53d6\u5bc6\u94a5\u5e93\u6216\u627e\u4e0d\u5230\u6307\u5b9a\u8bc1\u4e66
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
     * \u6838\u5bf9\u4e3b\u9898\u3001\u6709\u6548\u671f\u548c\u5f53\u524d\u73af\u5883\u7ed1\u5b9a\u8981\u6c42\u3002
     * @param licenseInfo \u5df2\u7ecf\u89e3\u6790\u7684\u8bb8\u53ef\u8bc1\u4fe1\u606f
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
     * \u5c06\u73af\u5883\u7ed1\u5b9a\u5931\u8d25\u4e0e\u5176\u4ed6\u8bb8\u53ef\u8bc1\u9a8c\u8bc1\u6545\u969c\u5206\u7c7b\u3002
     * @param exception \u9a8c\u8bc1\u8fc7\u7a0b\u4e2d\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u5bf9\u5916\u62a5\u544a\u7684\u5931\u8d25\u7c7b\u578b
     */
    private LicenseVerificationResult.Status statusFor(Exception exception) {
        String message = exception.getMessage();
        if (Objects.nonNull(message) && (message.contains("IP 校验") || message.contains("MAC 校验") || message.contains("SN 校验"))) {
            return LicenseVerificationResult.Status.ENVIRONMENT_MISMATCH;
        }
        return LicenseVerificationResult.Status.INVALID;
    }

    /**
     * \u6839\u636e\u4e3b\u9898\u3001\u516c\u94a5\u522b\u540d\u53ca\u89c4\u8303\u5316\u6587\u4ef6\u8def\u5f84\u751f\u6210\u9a8c\u8bc1\u7f13\u5b58\u952e\u3002
     * @return \u533a\u5206\u5f53\u524d\u8bb8\u53ef\u8bc1\u4e0e\u9a8c\u8bc1\u914d\u7f6e\u7684\u7f13\u5b58\u952e
     */
    private String buildCacheKey() {
        String normalizedLicensePath = Paths.get(licensePath).toAbsolutePath().normalize().toString();
        String normalizedKeyStorePath = Paths.get(publicKeysStorePath).toAbsolutePath().normalize().toString();
        return subject + ":" + Integer.toHexString(Objects.hash(publicAlias, normalizedLicensePath, normalizedKeyStorePath));
    }

}
