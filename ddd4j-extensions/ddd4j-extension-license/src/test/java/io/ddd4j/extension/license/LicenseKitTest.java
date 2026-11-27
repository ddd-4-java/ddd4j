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

    /**
     * 用于关联测试许可证或测试账号的主题标识。
     */
    private static final String SUBJECT = "ioserver-test";
    /**
     * 打开测试密钥库的口令。
     */
    private static final String STORE_PASS = "a123456";
    /**
     * 构造本组测试输入时复用的固定标识或名称。
     */
    private static final String KEY_PASS = "a123456";
    /**
     * 测试签发密钥在 JKS 中的别名。
     */
    private static final String PRIVATE_ALIAS = "privateKey";
    /**
     * 测试验签证书在 JKS 中的别名。
     */
    private static final String PUBLIC_ALIAS = "publicCert";

    /**
     * JUnit 为本次测试分配的临时目录。
     */
    @TempDir
    Path tempDir;

    /**
     * 测试使用的私钥库文件路径。
     */
    private String privateKeysStorePath;
    /**
     * 测试使用的公钥库文件路径。
     */
    private String publicKeysStorePath;
    /**
     * 测试生成、安装或读取的许可证文件路径。
     */
    private String licensePath;

    /**
     * 测试执行前执行 <code>tempDir.resolve()</code>、<code>CacheKit.unregister()</code>、<code>LicenseKeyStoreParam.builder()</code>、<code>result.getPrivateKeysStorePath()</code> 完成资源准备。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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

    /**
     * 测试后依次注销<code>CacheKit</code>。
     */
    @AfterEach
    void tearDown() {
        CacheKit.unregister(LicenseCache.BIZ_LICENSE);
    }

    /**
     * 验证<code>generated</code>为真，<code>installed</code>为真，<code>LicenseCache.exists(SUBJECT)</code>为假。
     */
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

    /**
     * 验证<code>verifier.verify()</code>为假，<code>verifier.verifyResult().getStatus()</code> 等于 <code>LicenseVerificationResult.Status.NOT_INSTALLED</code>，<code>verifier.isInstalled()</code>为假。
     */
    @Test
    void shouldReturnFalseWhenNotInstalled() {
        LicenseKit verifier = LicenseKit.of(SUBJECT, PUBLIC_ALIAS, STORE_PASS, licensePath, publicKeysStorePath);
        // 未安装直接 verify
        assertThat(verifier.verify()).isFalse();
        assertThat(verifier.verifyResult().getStatus())
                .isEqualTo(LicenseVerificationResult.Status.NOT_INSTALLED);
        assertThat(verifier.isInstalled()).isFalse();
    }

    /**
     * 验证<code>manager.verify()</code>为真。
     */
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

    /**
     * 验证<code>LicenseCache.exists(SUBJECT)</code>为真，<code>LicenseCache.exists(SUBJECT)</code>为假。
     */
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
