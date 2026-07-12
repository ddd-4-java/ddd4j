package io.ddd4j.extension.license;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.extension.license.creator.LicenseCreatorParam;
import io.ddd4j.extension.license.keystore.LicenseKeyStoreGenerator;
import io.ddd4j.extension.license.keystore.LicenseKeyStoreParam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LicenseKit} 端到端集成测试。
 *
 * <p>覆盖完整链路：keytool 生成公私钥库 → 签发 {@code .lic} → 安装 → 验证(命中缓存) → 卸载。
 * 使用 {@code @TempDir}，可在 macOS / Linux 直接跑通（不再写死 Windows 路径）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class LicenseKitTest {

    private static final String SUBJECT = "ioserver-test";
    private static final String STORE_PASS = "a123456";
    private static final String KEY_PASS = "a123456";
    private static final String PRIVATE_ALIAS = "privateKey";
    private static final String PUBLIC_ALIAS = "publicCert";

    @TempDir
    Path tempDir;

    private String privateKeysStorePath;
    private String publicKeysStorePath;
    private String licensePath;

    @BeforeEach
    void setUp() throws Exception {
        privateKeysStorePath = tempDir.resolve("privateKeys.keystore").toString();
        publicKeysStorePath = tempDir.resolve("publicCerts.keystore").toString();
        licensePath = tempDir.resolve("license.lic").toString();

        // 清理可能残留的缓存域
        CacheKit.unregister(LicenseCache.BIZ_LICENSE);

        // 1. 用 keytool 生成公私钥库
        LicenseKeyStoreParam ksParam = LicenseKeyStoreParam.builder()
                .privateAlias(PRIVATE_ALIAS)
                .publicAlias(PUBLIC_ALIAS)
                .storePass(STORE_PASS)
                .keyPass(KEY_PASS)
                .privateKeysStorePath(privateKeysStorePath)
                .publicKeysStorePath(publicKeysStorePath)
                .build();
        LicenseKeyStoreGenerator.GenerateResult result =
                new LicenseKeyStoreGenerator().generate(ksParam);
        assertThat(result.getPrivateKeysStorePath()).isEqualTo(privateKeysStorePath);
        assertThat(result.getPublicKeysStorePath()).isEqualTo(publicKeysStorePath);
    }

    @AfterEach
    void tearDown() {
        CacheKit.unregister(LicenseCache.BIZ_LICENSE);
    }

    @Test
    void shouldGenerateInstallVerifyAndCacheLicenseEndToEnd() {
        // 2. 签发证书
        LicenseCreatorParam param = new LicenseCreatorParam();
        param.setSubject(SUBJECT);
        param.setPrivateAlias(PRIVATE_ALIAS);
        param.setKeyPass(KEY_PASS);
        param.setStorePass(STORE_PASS);
        param.setLicensePath(licensePath);
        param.setPrivateKeysStorePath(privateKeysStorePath);
        Calendar issue = Calendar.getInstance();
        param.setIssuedTime(issue.getTime());
        Calendar expiry = Calendar.getInstance();
        expiry.add(Calendar.DAY_OF_YEAR, 30);
        param.setExpiryTime(expiry.getTime());
        param.setConsumerType("user");
        param.setConsumerAmount(1);
        param.setDescription("端到端测试证书");

        LicenseKit issuer = LicenseKit.of(SUBJECT, PUBLIC_ALIAS, STORE_PASS, licensePath, publicKeysStorePath);
        boolean generated = issuer.generate(param);
        assertThat(generated).isTrue();

        // 3. 安装（校验方使用公钥库）
        LicenseKit verifier = LicenseKit.of(SUBJECT, PUBLIC_ALIAS, STORE_PASS, licensePath, publicKeysStorePath);
        boolean installed = verifier.install();
        assertThat(installed).isTrue();

        // 4. 验证：首次走 TrueLicense 验签，install 已预填缓存
        assertThat(verifier.verify()).isTrue();

        // 5. 缓存命中：getLicenseInfo 应返回预填的 LicenseInfo
        LicenseInfo info = verifier.getLicenseInfo();
        assertThat(info).isNotNull();
        assertThat(info.getSubject()).isEqualTo(SUBJECT);
        assertThat(info.isValidNow()).isTrue();

        // 6. 缓存确实存在
        assertThat(LicenseCache.exists(SUBJECT)).isTrue();

        // 7. 再次 verify 走缓存（不重新验签）
        assertThat(verifier.verify()).isTrue();

        // 8. 卸载并清缓存
        verifier.uninstall();
        assertThat(LicenseCache.exists(SUBJECT)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenNotInstalled() {
        LicenseKit verifier = LicenseKit.of(SUBJECT, PUBLIC_ALIAS, STORE_PASS, licensePath, publicKeysStorePath);
        // 未安装直接 verify
        assertThat(verifier.verify()).isFalse();
        assertThat(verifier.isInstalled()).isFalse();
    }

    @Test
    void shouldBypassVerifyWhenDisabled() {
        LicenseProperties props = new LicenseProperties();
        props.setEnabled(false);
        props.setSubject(SUBJECT);
        props.setPublicAlias(PUBLIC_ALIAS);
        props.setStorePass(STORE_PASS);
        props.setLicensePath(licensePath);
        props.setPublicKeysStorePath(publicKeysStorePath);

        LicenseKit manager = LicenseKit.from(props);
        // 关闭后即使未安装也返回 true
        assertThat(manager.verify()).isTrue();
    }

    @Test
    void shouldEvictCache() {
        // 预填一个缓存项
        LicenseInfo fake = LicenseInfo.builder().subject(SUBJECT).build();
        LicenseCache.init();
        LicenseCache.put(SUBJECT, fake);
        assertThat(LicenseCache.exists(SUBJECT)).isTrue();

        LicenseKit verifier = LicenseKit.of(SUBJECT, PUBLIC_ALIAS, STORE_PASS, licensePath, publicKeysStorePath);
        verifier.evictCache();

        assertThat(LicenseCache.exists(SUBJECT)).isFalse();
    }
}
