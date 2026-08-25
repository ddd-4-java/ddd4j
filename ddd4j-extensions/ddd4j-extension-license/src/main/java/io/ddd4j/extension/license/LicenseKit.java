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

import io.ddd4j.extension.license.creator.LicenseCreator;
import io.ddd4j.extension.license.creator.LicenseCreatorParam;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/**
 * License 统一管理门面。
 *
 * <p>聚合签发（{@link LicenseCreator}）、校验（{@link LicenseVerify}）、缓存（{@link LicenseCache}）三段，
 * 对外提供完整的 License 生命周期 API。是业务代码与底层签名校验 / CacheKit 之间的唯一入口。
 *
 * <h3>两种使用模式</h3>
 * <ol>
 *   <li><b>发证方</b>（厂商）：{@link #generate(LicenseCreatorParam)} 生成 {@code .lic} 文件；</li>
 *   <li><b>校验方</b>（部署方）：{@link #install()} → {@link #verify()} → {@link #uninstall()}，
 *       验证结果自动缓存到 {@link LicenseCache}（CacheKit）。</li>
 * </ol>
 *
 * <h3>基于 {@link LicenseProperties} 的快速装配</h3>
 * <pre>{@code
 *   LicenseProperties props = ...; // 来自配置
 *   LicenseKit manager = LicenseKit.from(props);
 *   manager.install();
 *   if (manager.verify()) { ... }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class LicenseKit {

    private final LicenseProperties properties;
    private final LicenseVerify verify;

    /**
     * 私有构造：基于已就绪的配置创建校验端。
     *
     * @param properties License 配置
     */
    private LicenseKit(LicenseProperties properties) {
        this.properties = properties;
        this.verify = new LicenseVerify(
                properties.getSubject(),
                properties.getPublicAlias(),
                properties.getStorePass(),
                properties.getLicensePath(),
                properties.getPublicKeysStorePath());
        this.verify.setCacheTtlSeconds(properties.getCacheTtlSeconds());
    }

    /**
     * 从 {@link LicenseProperties} 装配一个 LicenseKit（校验端）。
     *
     * @param properties License 配置
     * @return LicenseKit 实例
     */
    public static LicenseKit from(LicenseProperties properties) {
        Objects.requireNonNull(properties, "LicenseProperties 不能为空");
        return new LicenseKit(properties);
    }

    /**
     * 直接基于校验端核心参数创建 LicenseKit。
     *
     * @param subject             证书 subject
     * @param publicAlias         公钥别称
     * @param storePass           公钥库密码
     * @param licensePath         {@code .lic} 文件路径
     * @param publicKeysStorePath 公钥库路径
     * @return LicenseKit 实例
     */
    public static LicenseKit of(String subject, String publicAlias, String storePass,
                                    String licensePath, String publicKeysStorePath) {
        LicenseProperties props = new LicenseProperties();
        props.setSubject(subject);
        props.setPublicAlias(publicAlias);
        props.setStorePass(storePass);
        props.setLicensePath(licensePath);
        props.setPublicKeysStorePath(publicKeysStorePath);
        return new LicenseKit(props);
    }

    // ==================== 签发（发证方） ====================

    /**
     * 生成 License 证书（{@code .lic}）。
     *
     * <p>这是发证方使用的方法，内部委托给 {@link LicenseCreator}。
     *
     * @param param 证书生成参数
     * @return true 表示生成成功
     */
    public boolean generate(LicenseCreatorParam param) {
        return new LicenseCreator(param).generateLicense();
    }

    /**
     * 便捷重载：用默认参数生成证书。
     *
     * @param subject             证书 subject
     * @param privateAlias        私钥别称
     * @param keyPass             私钥密码
     * @param storePass           私钥库密码
     * @param licensePath         {@code .lic} 输出路径
     * @param privateKeysStorePath 私钥库路径
     * @param expiryTime          失效时间
     * @param extra               附加校验信息（可 null）
     * @return true 表示生成成功
     */
    public boolean generate(String subject, String privateAlias, String keyPass, String storePass,
                            String licensePath, String privateKeysStorePath,
                            Date expiryTime, LicenseExtraModel extra) {
        LicenseCreatorParam param = new LicenseCreatorParam();
        param.setSubject(subject);
        param.setPrivateAlias(privateAlias);
        param.setKeyPass(keyPass);
        param.setStorePass(storePass);
        param.setLicensePath(licensePath);
        param.setPrivateKeysStorePath(privateKeysStorePath);
        param.setIssuedTime(new Date());
        param.setExpiryTime(Objects.nonNull(expiryTime) ? expiryTime : defaultExpiry());
        param.setConsumerType("user");
        param.setConsumerAmount(1);
        param.setDescription("");
        param.setLicenseExtraModel(extra);
        return generate(param);
    }

    // ==================== 校验（部署方） ====================

    /**
     * 安装证书。
     *
     * <p>读取 {@code .lic} 并安装到 Preferences，成功后预填缓存。
     *
     * @return true 表示安装成功
     */
    public boolean install() {
        verify.installLicense();
        return verify.isInstallSuccess();
    }

    /**
     * 卸载证书并清空缓存。
     */
    public void uninstall() {
        verify.unInstallLicense();
    }

    /**
     * 运行期校验证书是否有效。
     *
     * <p>当 {@link LicenseProperties#isEnabled()} 为 false 时恒返回 true（旁路）。
     * 否则先查 {@link LicenseCache}，命中且未过期直接通过；未命中重新验签并回填缓存。
     *
     * @return true 表示校验通过
     */
    public boolean verify() {
        return verifyResult().isValid();
    }

    /**
     * 返回结构化验证结果，用于接口响应、健康检查和审计。
     *
     * @return 验证结果
     */
    public LicenseVerificationResult verifyResult() {
        if (!properties.isEnabled()) {
            return LicenseVerificationResult.disabled();
        }
        return verify.verifyResult();
    }

    /**
     * 获取缓存中的证书信息（不触发验签）。
     *
     * @return {@link LicenseInfo}；缓存未命中返回 null
     */
    public LicenseInfo getLicenseInfo() {
        return verify.getLicenseInfo();
    }

    /**
     * 清空 License 缓存域（强制下次 verify 重新验签）。
     */
    public void evictCache() {
        LicenseCache.invalidateAll();
    }

    /**
     * @return 证书是否安装成功
     */
    public boolean isInstalled() {
        return verify.isInstallSuccess();
    }

    public boolean isCached() {
        return verify.isCached();
    }

    /**
     * @return License 配置（只读视图）
     */
    public LicenseProperties getProperties() {
        return properties;
    }

    /**
     * 默认失效时间：当前时间 + 1 年。
     */
    private Date defaultExpiry() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.YEAR, 1);
        return c.getTime();
    }
}
