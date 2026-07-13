package io.ddd4j.extension.license;

import de.schlichtherle.license.*;
import io.ddd4j.extension.license.manager.CustomLicenseManager;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * License 校验类（校验端 / 部署方使用）。
 *
 * <p>负责证书的安装、卸载与运行期校验。校验结果会缓存到 {@link LicenseCache}（CacheKit），
 * 避免每次请求都走昂贵的 TrueLicense 验签 + XML 解码。缓存命中时仅做轻量的有效期比对。
 *
 * <p>典型用法：
 * <pre>{@code
 *   LicenseVerify verify = new LicenseVerify(subject, publicAlias, storePass, licensePath, publicKeysStorePath);
 *   verify.installLicense();     // 安装证书（成功后预填缓存）
 *   boolean ok = verify.verify(); // 运行期校验（先查缓存，未命中才验签）
 *   verify.unInstallLicense();   // 卸载并清空缓存
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class LicenseVerify {

    /**
     * 证书subject
     */
    private final String subject;
    /**
     * 公钥别称
     */
    private final String publicAlias;
    /**
     * 访问公钥库的密码
     */
    private final String storePass;
    /**
     * 证书生成路径
     */
    private final String licensePath;
    /**
     * 密钥库存储路径
     */
    private final String publicKeysStorePath;
    /**
     * License 管理器，用于证书的安装、校验和卸载操作
     */
    private volatile CustomLicenseManager licenseManager;
    /**
     * 标识证书是否安装成功
     */
    private volatile boolean installSuccess;
    /**
     * 验证结果缓存 TTL（秒）
     */
    private long cacheTtlSeconds = LicenseCache.DEFAULT_TTL_SECONDS;
    /**
     * 隔离同 subject、不同证书路径或公钥上下文的缓存。
     */
    private final String cacheKey;

    /**
     * 构造函数。
     *
     * @param subject             证书 subject
     * @param publicAlias         公钥别称
     * @param storePass           访问公钥库的密码
     * @param licensePath         证书生成路径
     * @param publicKeysStorePath 密钥库存储路径
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
        this.cacheKey = buildCacheKey();
    }

    /**
     * 设置验证结果缓存 TTL（秒）。
     *
     * @param cacheTtlSeconds TTL，&lt;= 0 表示使用默认值
     */
    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds > 0 ? cacheTtlSeconds : LicenseCache.DEFAULT_TTL_SECONDS;
    }

    /**
     * @return 证书是否安装成功
     */
    public boolean isInstallSuccess() {
        return installSuccess;
    }

    /**
     * 安装License证书，读取证书相关的信息。
     *
     * <p>安装成功后会用证书内容预填缓存，使得首次 {@link #verify()} 即可命中。
     */
    public synchronized void installLicense() {
        CustomLicenseManager previousManager = licenseManager;
        boolean previousInstallSuccess = installSuccess;
        try {
            LicenseCache.init(cacheTtlSeconds);

            Preferences preferences = Preferences.userNodeForPackage(LicenseVerify.class);

            CipherParam cipherParam = new DefaultCipherParam(storePass);

            KeyStoreParam publicStoreParam = new CustomKeyStoreParam(LicenseVerify.class,
                    publicKeysStorePath,
                    publicAlias,
                    storePass,
                    null);

            LicenseParam licenseParam = new DefaultLicenseParam(subject, preferences, publicStoreParam, cipherParam);

            CustomLicenseManager candidateManager = new CustomLicenseManager(licenseParam);
            LicenseContent licenseContent = candidateManager.install(new File(licensePath));
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            licenseManager = candidateManager;
            installSuccess = true;
            log.info("------------------------------- 证书安装成功 -------------------------------");
            log.info(MessageFormat.format("证书有效期：{0} - {1}",
                    format.format(licenseContent.getNotBefore()), format.format(licenseContent.getNotAfter())));

            // 预填缓存，首次 verify 即可命中
            LicenseCache.put(cacheKey, LicenseInfo.from(licenseContent));
        } catch (Exception e) {
            licenseManager = previousManager;
            installSuccess = previousInstallSuccess;
            log.error("------------------------------- 证书安装失败 -------------------------------");
            log.error("License 安装失败: subject={}, path={}, msg={}", subject, licensePath, e.getMessage(), e);
        }
    }

    /**
     * 卸载证书，同时清空缓存。
     */
    public synchronized void unInstallLicense() {
        if (installSuccess) {
            try {
                licenseManager.uninstall();
            } catch (Exception e) {
                // ignore
            }
        }
        installSuccess = false;
        licenseManager = null;
        LicenseCache.invalidate(cacheKey);
    }

    /**
     * 校验License证书。
     *
     * <p>先查 {@link LicenseCache}：
     * <ul>
     *   <li>命中且仍在有效期内 → 直接返回 true（跳过验签）；</li>
     *   <li>命中但已过期 → 失效缓存，继续走 TrueLicense 验签；</li>
     *   <li>未命中 → 走 TrueLicense {@code licenseManager.verify()}，成功后回填缓存。</li>
     * </ul>
     *
     * @return true 表示校验通过
     */
    public boolean verify() {
        return verifyResult().isValid();
    }

    /**
     * 校验证书并返回结构化结果，便于网关、健康检查和审计日志使用。
     *
     * @return 验证结果
     */
    public synchronized LicenseVerificationResult verifyResult() {
        if (!installSuccess || Objects.isNull(licenseManager)) {
            log.warn("证书未安装成功，校验失败");
            return LicenseVerificationResult.failure(
                    LicenseVerificationResult.Status.NOT_INSTALLED, "证书未安装成功");
        }

        // 1. 先查缓存
        LicenseInfo cached = LicenseCache.get(cacheKey);
        if (Objects.nonNull(cached)) {
            if (cached.isValidNow()) {
                try {
                    licenseManager.validateExtra(cached.getExtra());
                    return LicenseVerificationResult.success(cached);
                } catch (LicenseContentException e) {
                    LicenseCache.invalidate(cacheKey);
                    log.warn("License 运行环境校验失败: subject={}, msg={}", subject, e.getMessage());
                    return LicenseVerificationResult.failure(
                            LicenseVerificationResult.Status.ENVIRONMENT_MISMATCH, e.getMessage());
                }
            }
            // 缓存命中但已过期，失效后重验
            log.info("缓存中的证书已过期，失效缓存并重新校验: subject={}", subject);
            LicenseCache.invalidate(cacheKey);
        }

        // 2. 走 TrueLicense 验签
        try {
            LicenseContent licenseContent = licenseManager.verify();
                LicenseInfo info = LicenseInfo.from(licenseContent);
            if (Objects.nonNull(info)) {
                LicenseCache.put(cacheKey, info);
            }
            return LicenseVerificationResult.success(info);
        } catch (Exception e) {
            log.warn("License 校验失败: subject={}, msg={}", subject, e.getMessage());
            LicenseCache.invalidate(cacheKey);
            return LicenseVerificationResult.failure(
                    LicenseVerificationResult.Status.INVALID, e.getMessage());
        }
    }

    /**
     * 获取最近一次缓存中的证书信息（不触发验签）。
     *
     * @return 缓存中的 {@link LicenseInfo}；缓存未命中返回 null
     */
    public LicenseInfo getLicenseInfo() {
        return LicenseCache.get(cacheKey);
    }

    public boolean isCached() {
        return LicenseCache.exists(cacheKey);
    }

    private String buildCacheKey() {
        String normalizedLicensePath = Paths.get(licensePath).toAbsolutePath().normalize().toString();
        String normalizedKeyStorePath = Paths.get(publicKeysStorePath).toAbsolutePath().normalize().toString();
        return subject + ":" + Integer.toHexString(
                Objects.hash(publicAlias, normalizedLicensePath, normalizedKeyStorePath));
    }

    private static void requireText(String value, String field) {
        if (Objects.isNull(value) || StrKit.isEmpty(value.trim())) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
