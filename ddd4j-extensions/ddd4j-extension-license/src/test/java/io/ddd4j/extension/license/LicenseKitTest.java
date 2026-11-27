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

    /** \u7528\u4e8e\u5173\u8054\u6d4b\u8bd5\u8bb8\u53ef\u8bc1\u6216\u6d4b\u8bd5\u8d26\u53f7\u7684\u4e3b\u9898\u6807\u8bc6\u3002 */
    private static final String SUBJECT = "ioserver-test";
    /** \u6253\u5f00\u6d4b\u8bd5\u5bc6\u94a5\u5e93\u7684\u53e3\u4ee4\u3002 */
    private static final String STORE_PASS = "a123456";
    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
    private static final String KEY_PASS = "a123456";
    /** \u6d4b\u8bd5\u7b7e\u53d1\u5bc6\u94a5\u5728 JKS \u4e2d\u7684\u522b\u540d\u3002 */
    private static final String PRIVATE_ALIAS = "privateKey";
    /** \u6d4b\u8bd5\u9a8c\u7b7e\u8bc1\u4e66\u5728 JKS \u4e2d\u7684\u522b\u540d\u3002 */
    private static final String PUBLIC_ALIAS = "publicCert";

    /** JUnit \u4e3a\u672c\u6b21\u6d4b\u8bd5\u5206\u914d\u7684\u4e34\u65f6\u76ee\u5f55\u3002 */
    @TempDir
    Path tempDir;

    /** \u6d4b\u8bd5\u4f7f\u7528\u7684\u79c1\u94a5\u5e93\u6587\u4ef6\u8def\u5f84\u3002 */
    private String privateKeysStorePath;
    /** \u6d4b\u8bd5\u4f7f\u7528\u7684\u516c\u94a5\u5e93\u6587\u4ef6\u8def\u5f84\u3002 */
    private String publicKeysStorePath;
    /** \u6d4b\u8bd5\u751f\u6210\u3001\u5b89\u88c5\u6216\u8bfb\u53d6\u7684\u8bb8\u53ef\u8bc1\u6587\u4ef6\u8def\u5f84\u3002 */
    private String licensePath;

    /**
     * \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>tempDir.resolve()</code>\u3001<code>CacheKit.unregister()</code>\u3001<code>LicenseKeyStoreParam.builder()</code>\u3001<code>result.getPrivateKeysStorePath()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6ce8\u9500<code>CacheKit</code>\u3002 */
    @AfterEach
    void tearDown() {
        CacheKit.unregister(LicenseCache.BIZ_LICENSE);
    }

    /** \u9a8c\u8bc1<code>generated</code>\u4e3a\u771f\uff0c<code>installed</code>\u4e3a\u771f\uff0c<code>LicenseCache.exists(SUBJECT)</code>\u4e3a\u5047\u3002 */
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
        assertThat(verifier.isCached()).isTrue();

        // 7. 再次 verify 走缓存（不重新验签）
        assertThat(verifier.verify()).isTrue();

        // 8. 卸载并清缓存
        verifier.uninstall();
        assertThat(LicenseCache.exists(SUBJECT)).isFalse();
    }

    /** \u9a8c\u8bc1<code>verifier.verify()</code>\u4e3a\u5047\uff0c<code>verifier.verifyResult().getStatus()</code> \u7b49\u4e8e <code>LicenseVerificationResult.Status.NOT_INSTALLED</code>\uff0c<code>verifier.isInstalled()</code>\u4e3a\u5047\u3002 */
    @Test
    void shouldReturnFalseWhenNotInstalled() {
        LicenseKit verifier = LicenseKit.of(SUBJECT, PUBLIC_ALIAS, STORE_PASS, licensePath, publicKeysStorePath);
        // 未安装直接 verify
        assertThat(verifier.verify()).isFalse();
        assertThat(verifier.verifyResult().getStatus())
                .isEqualTo(LicenseVerificationResult.Status.NOT_INSTALLED);
        assertThat(verifier.isInstalled()).isFalse();
    }

    /** \u9a8c\u8bc1<code>manager.verify()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>LicenseCache.exists(SUBJECT)</code>\u4e3a\u771f\uff0c<code>LicenseCache.exists(SUBJECT)</code>\u4e3a\u5047\u3002 */
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
