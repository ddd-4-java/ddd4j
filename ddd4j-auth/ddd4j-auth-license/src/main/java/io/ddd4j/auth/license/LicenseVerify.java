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

    private final String subject;
    private final String publicAlias;
    private final String storePass;
    private final String licensePath;
    private final String publicKeysStorePath;
    private final String signatureAlgorithm;
    private CustomLicenseManager licenseManager;
    private boolean installSuccess;

    public LicenseVerify(String subject, String publicAlias, String storePass, String licensePath,
                         String publicKeysStorePath) {
        this(subject, publicAlias, storePass, licensePath, publicKeysStorePath,
                CustomKeyStoreParam.LEGACY_SIGNATURE_ALGORITHM);
    }

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

    private void uninstallQuietly() {
        try {
            licenseManager.uninstall();
        } catch (Exception exception) {
            log.warn("证书清理失败，stage=uninstall, exceptionType={}", exception.getClass().getName());
        }
    }

    private void logValidity(License content) {
        if (Objects.nonNull(content.getNotBefore()) && Objects.nonNull(content.getNotAfter())) {
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            log.info("证书有效期：{} - {}", format.format(content.getNotBefore()), format.format(content.getNotAfter()));
        }
    }
}
