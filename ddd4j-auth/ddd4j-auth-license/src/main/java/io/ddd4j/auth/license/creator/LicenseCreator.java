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
package io.ddd4j.auth.license.creator;

import global.namespace.truelicense.api.License;
import global.namespace.truelicense.v1.V1;
import io.ddd4j.auth.license.CustomKeyStoreParam;
import io.ddd4j.auth.license.manager.CustomLicenseManager;
import lombok.extern.slf4j.Slf4j;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * License生成类 -- 用于license生成。
 */
@Slf4j
public class LicenseCreator {

    private static final X500Principal DEFAULT_HOLDER_AND_ISSUER =
            new X500Principal("CN=localhost, OU=localhost, O=localhost, L=SH, ST=SH, C=CN");

    private final LicenseCreatorParam param;

    public LicenseCreator(LicenseCreatorParam param) {
        this.param = Objects.requireNonNull(param, "param");
    }

    /**
     * 生成License证书。
     */
    public boolean generateLicense() {
        try {
            CustomKeyStoreParam keyStoreParam = initKeyStoreParam();
            CustomLicenseManager licenseManager = new CustomLicenseManager(param.getSubject(), keyStoreParam,
                    Preferences.userNodeForPackage(LicenseCreator.class));
            licenseManager.store(initLicenseContent(), new File(param.getLicensePath()));
            return true;
        } catch (Exception exception) {
            log.error("证书生成失败，subject={}", param.getSubject(), exception);
            return false;
        }
    }

    /**
     * 初始化证书生成密钥库参数。
     */
    private CustomKeyStoreParam initKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseCreator.class,
                param.getPrivateKeysStorePath(),
                param.getPrivateAlias(),
                param.getStorePass(),
                param.getKeyPass(),
                param.getSignatureAlgorithm());
    }

    /**
     * 设置证书生成正文信息。
     */
    private License initLicenseContent() {
        License content = V1.builder().subject(param.getSubject()).build().licenseFactory().license();
        content.setHolder(DEFAULT_HOLDER_AND_ISSUER);
        content.setIssuer(DEFAULT_HOLDER_AND_ISSUER);
        content.setSubject(param.getSubject());
        content.setIssued(param.getIssuedTime());
        content.setNotBefore(param.getIssuedTime());
        content.setNotAfter(param.getExpiryTime());
        content.setConsumerType(param.getConsumerType());
        content.setConsumerAmount(param.getConsumerAmount());
        content.setInfo(param.getDescription());
        if (Objects.nonNull(param.getLicenseExtraModel())) {
            content.setExtra(param.getLicenseExtraModel());
        }
        return content;
    }
}
