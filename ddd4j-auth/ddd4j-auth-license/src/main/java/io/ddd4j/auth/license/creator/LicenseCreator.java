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

    /** \u672a\u6307\u5b9a\u6301\u6709\u4eba\u6216\u7b7e\u53d1\u8005\u65f6\u4f7f\u7528\u7684\u9ed8\u8ba4\u4e3b\u4f53\u6807\u8bc6\u3002 */
    private static final X500Principal DEFAULT_HOLDER_AND_ISSUER =
            new X500Principal("CN=localhost, OU=localhost, O=localhost, L=SH, ST=SH, C=CN");

    /** \u751f\u6210\u8bb8\u53ef\u8bc1\u6240\u9700\u7684\u7b7e\u53d1\u914d\u7f6e\u548c\u4e1a\u52a1\u5185\u5bb9\u3002 */
    private final LicenseCreatorParam param;

    /**
     * \u4f7f\u7528\u8bb8\u53ef\u8bc1\u751f\u6210\u53c2\u6570\u521b\u5efa\u751f\u6210\u5668\u3002
     * @param param \u8bb8\u53ef\u8bc1\u751f\u6210\u53c2\u6570
     */
    public LicenseCreator(LicenseCreatorParam param) {
        this.param = Objects.requireNonNull(param, "param");
    }

    /**
     * 生成License证书。
     * @return 条件成立（或操作成功）返回 true，否则返回 false
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
     * \u521d\u59cb\u5316\u8bc1\u4e66\u751f\u6210\u5bc6\u94a5\u5e93\u53c2\u6570\u3002
     *
     * @return \u542b\u5bc6\u94a5\u5e93\u8def\u5f84\u3001\u522b\u540d\u548c\u51ed\u636e\u7684\u53c2\u6570
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
     * \u8bbe\u7f6e\u8bc1\u4e66\u751f\u6210\u6b63\u6587\u4fe1\u606f\u3002
     *
     * @return \u5f85\u7b7e\u53d1\u7684\u8bb8\u53ef\u8bc1\u5185\u5bb9
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
