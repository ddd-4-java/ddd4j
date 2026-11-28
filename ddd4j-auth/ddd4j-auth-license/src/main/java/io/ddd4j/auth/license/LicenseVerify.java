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

    /** \u5f53\u524d\u670d\u52a1\u63a5\u53d7\u7684\u8bb8\u53ef\u8bc1\u4e3b\u9898\u3002 */
    private final String subject;
    /** \u7528\u4e8e\u9a8c\u8bc1\u7b7e\u540d\u7684\u516c\u94a5\u8bc1\u4e66\u522b\u540d\u3002 */
    private final String publicAlias;
    /** \u6253\u5f00\u5bc6\u94a5\u5e93\u6240\u9700\u7684\u5b58\u50a8\u53e3\u4ee4\u3002 */
    private final String storePass;
    /** \u5b89\u88c5\u6216\u9a8c\u8bc1\u7684\u8bb8\u53ef\u8bc1\u6587\u4ef6\u8def\u5f84\u3002 */
    private final String licensePath;
    /** \u5305\u542b\u9a8c\u8bc1\u516c\u94a5\u7684\u5bc6\u94a5\u5e93\u8def\u5f84\u3002 */
    private final String publicKeysStorePath;
    /** \u7528\u4e8e\u7b7e\u53d1\u6216\u9a8c\u8bc1\u8bb8\u53ef\u8bc1\u7684\u7b7e\u540d\u7b97\u6cd5\u3002 */
    private final String signatureAlgorithm;
    /** \u8d1f\u8d23\u8bb8\u53ef\u8bc1\u5b89\u88c5\u3001\u9a8c\u8bc1\u548c\u5378\u8f7d\u7684\u7ec4\u4ef6\u3002 */
    private CustomLicenseManager licenseManager;
    /** \u5f53\u524d\u8bb8\u53ef\u8bc1\u662f\u5426\u6210\u529f\u5b89\u88c5\u3002 */
    private boolean installSuccess;

    /**
     * \u4f7f\u7528\u517c\u5bb9\u7b7e\u540d\u7b97\u6cd5\u521b\u5efa\u8bb8\u53ef\u8bc1\u6821\u9a8c\u5668\u3002
     * @param subject \u8bb8\u53ef\u8bc1\u4e3b\u9898
     * @param publicAlias \u516c\u94a5\u522b\u540d
     * @param storePass \u5bc6\u94a5\u5e93\u5bc6\u7801
     * @param licensePath \u8bb8\u53ef\u8bc1\u8def\u5f84
     * @param publicKeysStorePath \u516c\u94a5\u5e93\u8def\u5f84
     */
    public LicenseVerify(String subject, String publicAlias, String storePass, String licensePath,
                         String publicKeysStorePath) {
        this(subject, publicAlias, storePass, licensePath, publicKeysStorePath,
                CustomKeyStoreParam.LEGACY_SIGNATURE_ALGORITHM);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u7b7e\u540d\u7b97\u6cd5\u521b\u5efa\u8bb8\u53ef\u8bc1\u6821\u9a8c\u5668\u3002
     * @param subject \u8bb8\u53ef\u8bc1\u4e3b\u9898
     * @param publicAlias \u516c\u94a5\u522b\u540d
     * @param storePass \u5bc6\u94a5\u5e93\u5bc6\u7801
     * @param licensePath \u8bb8\u53ef\u8bc1\u8def\u5f84
     * @param publicKeysStorePath \u516c\u94a5\u5e93\u8def\u5f84
     * @param signatureAlgorithm \u7b7e\u540d\u7b97\u6cd5
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
     * \u6821\u9a8cLicense\u8bc1\u4e66\u3002
     *
     * @return \u8bc1\u4e66\u5df2\u5b89\u88c5\u4e14\u6821\u9a8c\u901a\u8fc7\u65f6\u4e3a true
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

    /** \u5c1d\u8bd5\u5378\u8f7d\u5f53\u524d\u8bb8\u53ef\u8bc1\uff0c\u6e05\u7406\u5931\u8d25\u65f6\u8bb0\u5f55\u5931\u8d25\u9636\u6bb5\u548c\u5f02\u5e38\u7c7b\u578b\u3002 */
    private void uninstallQuietly() {
        try {
            licenseManager.uninstall();
        } catch (Exception exception) {
            log.warn("证书清理失败，stage=uninstall, exceptionType={}", exception.getClass().getName());
        }
    }

    /**
     * \u5f53\u8d77\u6b62\u65e5\u671f\u90fd\u6709\u503c\u65f6\u8bb0\u5f55\u8bb8\u53ef\u8bc1\u6709\u6548\u671f\u3002
     * @param content \u5df2\u8bfb\u53d6\u7684\u8bb8\u53ef\u8bc1\u5185\u5bb9
     */
    private void logValidity(License content) {
        if (Objects.nonNull(content.getNotBefore()) && Objects.nonNull(content.getNotAfter())) {
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            log.info("证书有效期：{} - {}", format.format(content.getNotBefore()), format.format(content.getNotAfter()));
        }
    }
}
