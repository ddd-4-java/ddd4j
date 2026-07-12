package io.ddd4j.extension.license;

import de.schlichtherle.license.*;
import io.ddd4j.extension.license.manager.CustomLicenseManager;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
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
    private LicenseManager licenseManager;
    /**
     * 标识证书是否安装成功
     */
    private boolean installSuccess;
    /**
     * 验证结果缓存 TTL（秒）
     */
    private long cacheTtlSeconds = LicenseCache.DEFAULT_TTL_SECONDS;

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
        this.subject = subject;
        this.publicAlias = publicAlias;
        this.storePass = storePass;
        this.licensePath = licensePath;
        this.publicKeysStorePath = publicKeysStorePath;
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
    public void installLicense() {
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

            licenseManager = new CustomLicenseManager(licenseParam);
            licenseManager.uninstall();
            LicenseContent licenseContent = licenseManager.install(new File(licensePath));
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            installSuccess = true;
            log.info("------------------------------- 证书安装成功 -------------------------------");
            log.info(MessageFormat.format("证书有效期：{0} - {1}",
                    format.format(licenseContent.getNotBefore()), format.format(licenseContent.getNotAfter())));

            // 预填缓存，首次 verify 即可命中
            LicenseCache.put(subject, LicenseInfo.from(licenseContent));
        } catch (Exception e) {
            installSuccess = false;
            log.error("------------------------------- 证书安装失败 -------------------------------");
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 卸载证书，同时清空缓存。
     */
    public void unInstallLicense() {
        if (installSuccess) {
            try {
                licenseManager.uninstall();
            } catch (Exception e) {
                // ignore
            }
        }
        if (Objects.nonNull(subject)) {
            LicenseCache.invalidate(subject);
        }
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
        if (!installSuccess || Objects.isNull(licenseManager)) {
            log.warn("证书未安装成功，校验失败");
            return false;
        }

        // 1. 先查缓存
        LicenseInfo cached = LicenseCache.get(subject);
        if (Objects.nonNull(cached)) {
            if (cached.isValidNow()) {
                return true;
            }
            // 缓存命中但已过期，失效后重验
            log.info("缓存中的证书已过期，失效缓存并重新校验: subject={}", subject);
            LicenseCache.invalidate(subject);
        }

        // 2. 走 TrueLicense 验签
        try {
            LicenseContent licenseContent = licenseManager.verify();
            LicenseInfo info = LicenseInfo.from(licenseContent);
            if (Objects.nonNull(info)) {
                LicenseCache.put(subject, info);
            }
            return true;
        } catch (Exception e) {
            log.warn("License 校验失败: subject={}, msg={}", subject, e.getMessage());
            LicenseCache.invalidate(subject);
            return false;
        }
    }

    /**
     * 获取最近一次缓存中的证书信息（不触发验签）。
     *
     * @return 缓存中的 {@link LicenseInfo}；缓存未命中返回 null
     */
    public LicenseInfo getLicenseInfo() {
        return LicenseCache.get(subject);
    }
}
