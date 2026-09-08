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
package io.ddd4j.auth.license.manager;

import global.namespace.fun.io.api.Source;
import global.namespace.truelicense.api.ConsumerLicenseManager;
import global.namespace.truelicense.api.ConsumerLicenseManagerBuilder;
import global.namespace.truelicense.api.License;
import global.namespace.truelicense.api.LicenseFunctionComposition;
import global.namespace.truelicense.api.LicenseManagementContext;
import global.namespace.truelicense.api.LicenseManagementException;
import global.namespace.truelicense.api.LicenseValidationException;
import global.namespace.truelicense.api.VendorLicenseManager;
import global.namespace.truelicense.api.VendorLicenseManagerBuilder;
import global.namespace.truelicense.api.auth.AuthenticationChildBuilder;
import global.namespace.truelicense.api.crypto.EncryptionChildBuilder;
import global.namespace.truelicense.api.i18n.Message;
import global.namespace.truelicense.api.passwd.Password;
import global.namespace.truelicense.api.passwd.PasswordProtection;
import global.namespace.truelicense.v1.V1;
import io.ddd4j.auth.license.CustomKeyStoreParam;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.prefs.Preferences;

import static global.namespace.fun.io.bios.BIOS.file;
import static global.namespace.fun.io.bios.BIOS.preferences;

/**
 * 基于 TrueLicense 4.x 组合 API 的 V1 格式许可证管理器。
 */
public class CustomLicenseManager {

    private final ConsumerLicenseManager consumer;
    private final boolean userPreferences;
    private final VendorLicenseManager vendor;
    private boolean verificationInvalidated;

    public CustomLicenseManager(String subject, CustomKeyStoreParam keyStoreParam, Preferences preferences) {
        requireText(subject, "subject");
        Objects.requireNonNull(keyStoreParam, "keyStoreParam");
        Objects.requireNonNull(preferences, "preferences");
        this.userPreferences = preferences.isUserNode();
        this.vendor = vendor(subject, keyStoreParam);
        this.consumer = consumer(subject, keyStoreParam, preferences);
    }

    /**
     * 生成许可证并写入指定文件。
     */
    public synchronized void store(License content, File target) throws LicenseManagementException {
        vendor.generateKeyFrom(Objects.requireNonNull(content, "content"))
                .saveTo(file(Objects.requireNonNull(target, "target")));
    }

    /**
     * 安装许可证，随后立即执行业务校验并加载许可证正文。
     */
    public synchronized License install(File source) throws LicenseManagementException {
        verificationInvalidated = true;
        try {
            consumer.install(file(Objects.requireNonNull(source, "source")));
            consumer.verify();
            License installed = consumer.load();
            verificationInvalidated = false;
            return installed;
        } catch (LicenseManagementException exception) {
            cleanupFailedInstallation(exception);
            throw exception;
        } catch (RuntimeException | Error exception) {
            cleanupFailedInstallation(exception);
            throw exception;
        }
    }

    /**
     * 校验并返回当前已安装的许可证正文。
     */
    public synchronized License verify() throws LicenseManagementException {
        if (verificationInvalidated) {
            throw invalidatedException();
        }
        consumer.verify();
        return consumer.load();
    }

    /**
     * 卸载当前许可证。
     */
    public synchronized void uninstall() throws LicenseManagementException {
        verificationInvalidated = true;
        consumer.uninstall();
    }

    /**
     * 保留旧生成端的时间约束：允许签发尚未生效的许可证，但拒绝已过期或反向时间区间。
     */
    protected synchronized void validateCreate(License content) throws LicenseValidationException {
        Objects.requireNonNull(content, "content");
        Date now = new Date();
        Date notBefore = content.getNotBefore();
        Date notAfter = content.getNotAfter();
        if (Objects.nonNull(notAfter) && now.after(notAfter)) {
            throw validationException("证书失效时间不能早于当前时间");
        }
        if (Objects.nonNull(notBefore) && Objects.nonNull(notAfter) && notAfter.before(notBefore)) {
            throw validationException("证书生效时间不能晚于证书失效时间");
        }
        if (Objects.isNull(content.getConsumerType())) {
            throw validationException("用户类型不能为空");
        }
    }

    /**
     * 消费端扩展校验钩子。当前无额外硬件约束。
     */
    protected synchronized void validate(License content) throws LicenseValidationException {
        Objects.requireNonNull(content, "content");
    }

    private VendorLicenseManager vendor(String subject, CustomKeyStoreParam keyStoreParam) {
        LicenseManagementContext context = V1.builder()
                .subject(subject)
                .validation(this::validateCreate)
                .validationComposition(LicenseFunctionComposition.override)
                .build();
        VendorLicenseManagerBuilder builder = context.vendor();
        AuthenticationChildBuilder<? extends VendorLicenseManagerBuilder> authentication = builder.authentication()
                .algorithm(keyStoreParam.getSignatureAlgorithm())
                .alias(keyStoreParam.getAlias())
                .loadFrom(keyStoreSource(keyStoreParam))
                .storeProtection(passwordProtection(keyStoreParam.getStorePwd()));
        if (StringUtils.hasText(keyStoreParam.getKeyPwd())) {
            authentication.keyProtection(passwordProtection(keyStoreParam.getKeyPwd()));
        }
        builder = authentication.up();
        EncryptionChildBuilder<? extends VendorLicenseManagerBuilder> encryption = builder.encryption()
                .protection(passwordProtection(keyStoreParam.getStorePwd()));
        return encryption.up().build();
    }

    private ConsumerLicenseManager consumer(String subject, CustomKeyStoreParam keyStoreParam,
                                            Preferences preferencesNode) {
        LicenseManagementContext context = V1.builder()
                .subject(subject)
                .cachePeriodMillis(0L)
                .validation(this::validateConsumer)
                .build();
        ConsumerLicenseManagerBuilder builder = context.consumer();
        AuthenticationChildBuilder<? extends ConsumerLicenseManagerBuilder> authentication = builder.authentication()
                .algorithm(keyStoreParam.getSignatureAlgorithm())
                .alias(keyStoreParam.getAlias())
                .loadFrom(keyStoreSource(keyStoreParam))
                .storeProtection(passwordProtection(keyStoreParam.getStorePwd()));
        builder = authentication.up();
        EncryptionChildBuilder<? extends ConsumerLicenseManagerBuilder> encryption = builder.encryption()
                .protection(passwordProtection(keyStoreParam.getStorePwd()));
        return encryption.up().storeIn(preferences(preferencesNode, subject)).build();
    }

    private static Source keyStoreSource(CustomKeyStoreParam keyStoreParam) {
        return () -> keyStoreParam::getStream;
    }

    private void validateConsumer(License content) throws LicenseValidationException {
        Objects.requireNonNull(content, "content");
        String consumerType = content.getConsumerType();
        int consumerAmount = content.getConsumerAmount();
        if (userPreferences) {
            if (!"user".equalsIgnoreCase(consumerType)) {
                throw validationException("用户偏好节点只接受user类型许可证");
            }
            if (consumerAmount != 1) {
                throw validationException("用户偏好节点只接受单用户许可证");
            }
        } else {
            if (Objects.isNull(consumerType)) {
                throw validationException("用户类型不能为空");
            }
            if (consumerAmount <= 0) {
                throw validationException("用户数量必须为正数");
            }
        }
        validate(content);
    }

    private void cleanupFailedInstallation(Throwable primary) {
        try {
            consumer.uninstall();
        } catch (LicenseManagementException | RuntimeException | Error cleanupException) {
            primary.addSuppressed(cleanupException);
        }
    }

    private static LicenseManagementException invalidatedException() {
        return new LicenseManagementException(
                new IllegalStateException("license manager verification state has been invalidated"));
    }

    private static PasswordProtection passwordProtection(String value) {
        Objects.requireNonNull(value, "password");
        return usage -> new Password() {
            private final char[] characters = value.toCharArray();

            @Override
            public char[] characters() {
                return characters;
            }

            @Override
            public void close() {
                Arrays.fill(characters, (char) 0);
            }
        };
    }

    private static LicenseValidationException validationException(String text) {
        return new LicenseValidationException(new PlainMessage(text));
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static final class PlainMessage implements Message {

        private static final long serialVersionUID = 1L;

        private final String text;

        private PlainMessage(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }

        @Override
        public String toString(Locale locale) {
            return text;
        }
    }
}
