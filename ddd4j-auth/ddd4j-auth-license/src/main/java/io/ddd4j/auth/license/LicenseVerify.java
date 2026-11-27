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
package io.ddd4j.auth.license;

import global.namespace.truelicense.api.License;
import io.ddd4j.auth.license.manager.CustomLicenseManager;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * License校验类。
 */
@Slf4j
public class LicenseVerify {

    /**
     * 当前服务接受的许可证主题。
     */
    private final String subject;
    /**
     * 用于验证签名的公钥证书别名。
     */
    private final String publicAlias;
    /**
     * 打开密钥库所需的存储口令。
     */
    private final String storePass;
    /**
     * 安装或验证的许可证文件路径。
     */
    private final String licensePath;
    /**
     * 包含验证公钥的密钥库路径。
     */
    private final String publicKeysStorePath;
    /**
     * 用于签发或验证许可证的签名算法。
     */
    private final String signatureAlgorithm;
    /**
     * 负责许可证安装、验证和卸载的组件。
     */
    private CustomLicenseManager licenseManager;
    /**
     * 当前许可证是否成功安装。
     */
    private boolean installSuccess;

    /**
     * 使用兼容签名算法创建许可证校验器。
     *
     * @param subject             许可证主题
     * @param publicAlias         公钥别名
     * @param storePass           密钥库密码
     * @param licensePath         许可证路径
     * @param publicKeysStorePath 公钥库路径
     */
    public LicenseVerify(String subject, String publicAlias, String storePass, String licensePath,
                         String publicKeysStorePath) {
        this(subject, publicAlias, storePass, licensePath, publicKeysStorePath,
                CustomKeyStoreParam.LEGACY_SIGNATURE_ALGORITHM);
    }

    /**
     * 使用指定签名算法创建许可证校验器。
     *
     * @param subject             许可证主题
     * @param publicAlias         公钥别名
     * @param storePass           密钥库密码
     * @param licensePath         许可证路径
     * @param publicKeysStorePath 公钥库路径
     * @param signatureAlgorithm  签名算法
     */
    public LicenseVerify(String subject, String publicAlias, String storePass, String licensePath,
                         String publicKeysStorePath, String signatureAlgorithm) {
        this.subject = subject;
        this.publicAlias = publicAlias;
        this.storePass = storePass;
        this.licensePath = licensePath;
        this.publicKeysStorePath = publicKeysStorePath;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    /**
     * 安装License证书，读取证书相关的信息，在Bean加入容器的时候自动调用。
     */
    public void installLicense() {
        try {
            CustomKeyStoreParam publicStoreParam = new CustomKeyStoreParam(LicenseVerify.class,
                    publicKeysStorePath, publicAlias, storePass, null, signatureAlgorithm);
            licenseManager = new CustomLicenseManager(subject, publicStoreParam,
                    Preferences.userNodeForPackage(LicenseVerify.class));
            uninstallQuietly();
            License content = licenseManager.install(new File(licensePath));
            installSuccess = true;
            log.info("------------------------------- 证书安装成功 -------------------------------");
            logValidity(content);
        } catch (Exception exception) {
            installSuccess = false;
            log.error("证书安装失败，stage=install, exceptionType={}", exception.getClass().getName());
        }
    }

    /**
     * 卸载证书，在Bean从容器移除的时候自动调用。
     */
    public void unInstallLicense() {
        if (installSuccess && Objects.nonNull(licenseManager)) {
            installSuccess = false;
            uninstallQuietly();
        }
    }

    /**
     * 校验License证书。
     *
     * @return 证书已安装且校验通过时为 true
     */
    public boolean verify() {
        if (!installSuccess || Objects.isNull(licenseManager)) {
            return false;
        }
        try {
            licenseManager.verify();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 尝试卸载当前许可证，清理失败时记录失败阶段和异常类型。
     */
    private void uninstallQuietly() {
        try {
            licenseManager.uninstall();
        } catch (Exception exception) {
            log.warn("证书清理失败，stage=uninstall, exceptionType={}", exception.getClass().getName());
        }
    }

    /**
     * 当起止日期都有值时记录许可证有效期。
     *
     * @param content 已读取的许可证内容
     */
    private void logValidity(License content) {
        if (Objects.nonNull(content.getNotBefore()) && Objects.nonNull(content.getNotAfter())) {
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            log.info("证书有效期：{} - {}", format.format(content.getNotBefore()), format.format(content.getNotAfter()));
        }
    }
}
