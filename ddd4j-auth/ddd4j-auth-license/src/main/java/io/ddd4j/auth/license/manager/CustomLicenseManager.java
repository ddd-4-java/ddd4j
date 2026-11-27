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
import global.namespace.truelicense.api.VendorLicenseManagerBuilder;
import global.namespace.truelicense.api.VendorLicenseManager;
import global.namespace.truelicense.api.LicenseValidationException;
import global.namespace.truelicense.api.LicenseManagementException;
import global.namespace.truelicense.api.LicenseManagementContext;
import global.namespace.truelicense.api.LicenseFunctionComposition;
import global.namespace.truelicense.api.License;
import global.namespace.truelicense.api.ConsumerLicenseManagerBuilder;
import global.namespace.truelicense.api.ConsumerLicenseManager;

import global.namespace.fun.io.api.Source;
import global.namespace.truelicense.api.*;
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

    /** \u7528\u4e8e\u8bb8\u53ef\u8bc1\u5b89\u88c5\u3001\u5378\u8f7d\u548c\u5728\u7ebf\u9a8c\u8bc1\u7684 TrueLicense \u6d88\u8d39\u7aef\u7ba1\u7406\u5668\u3002 */
    private final ConsumerLicenseManager consumer;
    /** \u662f\u5426\u4f7f\u7528\u7528\u6237\u7ea7\u504f\u597d\u5b58\u50a8\u4fdd\u5b58\u8bb8\u53ef\u8bc1\u3002 */
    private final boolean userPreferences;
    /** \u8d1f\u8d23\u6309\u521b\u5efa\u7ea6\u675f\u7b7e\u53d1\u8bb8\u53ef\u8bc1\u7684 TrueLicense \u7ba1\u7406\u5668\u3002 */
    private final VendorLicenseManager vendor;
    /** \u8bb0\u5f55\u5b89\u88c5\u6216\u9a8c\u8bc1\u5931\u8d25\u540e\u5f53\u524d\u7ba1\u7406\u5668\u7684\u9a8c\u8bc1\u72b6\u6001\u662f\u5426\u5df2\u7ecf\u5931\u6548\u3002 */
    private boolean verificationInvalidated;

    /**
     * \u4f7f\u7528\u8bb8\u53ef\u8bc1\u4e3b\u9898\u3001\u5bc6\u94a5\u5e93\u548c\u5b58\u50a8\u8282\u70b9\u521b\u5efa\u7ba1\u7406\u5668\u3002
     * @param subject \u8bb8\u53ef\u8bc1\u4e3b\u9898
     * @param keyStoreParam \u5bc6\u94a5\u5e93\u53c2\u6570
     * @param preferences \u8bb8\u53ef\u8bc1\u5b58\u50a8\u8282\u70b9
     */
    public CustomLicenseManager(String subject, CustomKeyStoreParam keyStoreParam, Preferences preferences) {
        requireText(subject, "subject");
        Objects.requireNonNull(keyStoreParam, "keyStoreParam");
        Objects.requireNonNull(preferences, "preferences");
        this.userPreferences = preferences.isUserNode();
        this.vendor = vendor(subject, keyStoreParam);
        this.consumer = consumer(subject, keyStoreParam, preferences);
    }

    /**
     * \u628a\u5df2\u6709\u5bc6\u94a5\u5e93\u8f93\u5165\u5305\u88c5\u4e3a TrueLicense \u53ef\u6309\u9700\u8bfb\u53d6\u7684\u6e90\u3002
     * @param keyStoreParam \u63d0\u4f9b\u5bc6\u94a5\u5e93\u8f93\u5165\u7684\u914d\u7f6e
     * @return \u7531\u5bc6\u94a5\u5e93\u914d\u7f6e\u6253\u5f00\u8f93\u5165\u6d41\u7684\u8bb8\u53ef\u8bc1\u8d44\u6e90\u6e90
     */
    private static Source keyStoreSource(CustomKeyStoreParam keyStoreParam) {
        return () -> keyStoreParam::getStream;
    }

    /**
     * \u6784\u9020\u62d2\u7edd\u5bf9\u5df2\u5931\u6548\u7ba1\u7406\u5668\u7ee7\u7eed\u6267\u884c\u9a8c\u8bc1\u7684\u5f02\u5e38\u3002
     * @return \u5c01\u88c5\u9a8c\u8bc1\u72b6\u6001\u5931\u6548\u539f\u56e0\u7684\u7ba1\u7406\u5f02\u5e38
     */
    private static LicenseManagementException invalidatedException() {
        return new LicenseManagementException(
                new IllegalStateException("license manager verification state has been invalidated"));
    }

    /**
     * \u628a\u73b0\u6709\u53e3\u4ee4\u8f6c\u6362\u4e3a TrueLicense \u53ef\u5173\u95ed\u7684\u5b57\u7b26\u6570\u7ec4\u4fdd\u62a4\u5bf9\u8c61\u3002
     * @param value \u5bc6\u94a5\u5e93\u6216\u5bc6\u94a5\u6761\u76ee\u7684\u53e3\u4ee4
     * @return \u5728\u5173\u95ed\u65f6\u6e05\u7a7a\u5b57\u7b26\u6570\u7ec4\u7684\u53e3\u4ee4\u4fdd\u62a4\u5bf9\u8c61
     */
    private static PasswordProtection passwordProtection(String value) {
        Objects.requireNonNull(value, "password");
        return usage -> new Password() {
            private final char[] characters = value.toCharArray();

            /**
             * characters() 方法。
             * @return 方法执行结果。
             */
            @Override
            public char[] characters() {
                return characters;
            }

            /**
             * 关闭并释放资源。
             */
            @Override
            public void close() {
                Arrays.fill(characters, (char) 0);
            }
        };
    }

    /**
     * \u5c06\u8bb8\u53ef\u8bc1\u6821\u9a8c\u5931\u8d25\u4fe1\u606f\u8f6c\u6362\u6210 TrueLicense \u6821\u9a8c\u5f02\u5e38\u3002
     * @param text \u8bca\u65ad\u6240\u7528\u7684\u7eaf\u6587\u672c\u6d88\u606f
     * @return \u5e26\u539f\u59cb\u6d88\u606f\u7684\u8bb8\u53ef\u8bc1\u6821\u9a8c\u5f02\u5e38
     */
    private static LicenseValidationException validationException(String text) {
        return new LicenseValidationException(new PlainMessage(text));
    }

    /**
     * \u8981\u6c42\u5fc5\u586b\u8bb8\u53ef\u8bc1\u914d\u7f6e\u5305\u542b\u975e\u7a7a\u767d\u5b57\u7b26\u3002
     * @param value \u5f85\u6821\u9a8c\u7684\u503c
     * @param name \u7528\u4e8e\u5b9a\u4f4d\u9519\u8bef\u7684\u914d\u7f6e\u540d\u79f0
     * @throws IllegalArgumentException \u914d\u7f6e\u7f3a\u5931\u6216\u53ea\u542b\u7a7a\u767d\u5b57\u7b26
     */
    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /**
     * \u751f\u6210\u8bb8\u53ef\u8bc1\u5e76\u5199\u5165\u6307\u5b9a\u6587\u4ef6\u3002
     *
     * @param content \u8bb8\u53ef\u8bc1\u6b63\u6587
     * @param target \u8f93\u51fa\u6587\u4ef6
     * @throws LicenseManagementException \u751f\u6210\u6216\u5199\u5165\u5931\u8d25\u65f6
     */
    public synchronized void store(License content, File target) throws LicenseManagementException {
        vendor.generateKeyFrom(Objects.requireNonNull(content, "content"))
                .saveTo(file(Objects.requireNonNull(target, "target")));
    }

    /**
     * \u5b89\u88c5\u8bb8\u53ef\u8bc1\uff0c\u968f\u540e\u7acb\u5373\u6267\u884c\u4e1a\u52a1\u6821\u9a8c\u5e76\u52a0\u8f7d\u8bb8\u53ef\u8bc1\u6b63\u6587\u3002
     *
     * @param source \u8bb8\u53ef\u8bc1\u6587\u4ef6
     * @return \u5df2\u5b89\u88c5\u7684\u8bb8\u53ef\u8bc1\u6b63\u6587
     * @throws LicenseManagementException \u5b89\u88c5\u6216\u6821\u9a8c\u5931\u8d25\u65f6
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
     * \u6821\u9a8c\u5e76\u8fd4\u56de\u5f53\u524d\u5df2\u5b89\u88c5\u7684\u8bb8\u53ef\u8bc1\u6b63\u6587\u3002
     *
     * @return \u5df2\u5b89\u88c5\u7684\u8bb8\u53ef\u8bc1\u6b63\u6587
     * @throws LicenseManagementException \u6821\u9a8c\u6216\u8bfb\u53d6\u5931\u8d25\u65f6
     */
    public synchronized License verify() throws LicenseManagementException {
        if (verificationInvalidated) {
            throw invalidatedException();
        }
        consumer.verify();
        return consumer.load();
    }

    /**
     * \u5378\u8f7d\u5f53\u524d\u8bb8\u53ef\u8bc1\u3002
     *
     * @throws LicenseManagementException \u5378\u8f7d\u5931\u8d25\u65f6
     */
    public synchronized void uninstall() throws LicenseManagementException {
        verificationInvalidated = true;
        consumer.uninstall();
    }

    /**
     * \u4fdd\u7559\u65e7\u751f\u6210\u7aef\u7684\u65f6\u95f4\u7ea6\u675f\uff1a\u5141\u8bb8\u7b7e\u53d1\u5c1a\u672a\u751f\u6548\u7684\u8bb8\u53ef\u8bc1\uff0c\u4f46\u62d2\u7edd\u5df2\u8fc7\u671f\u6216\u53cd\u5411\u65f6\u95f4\u533a\u95f4\u3002
     *
     * @param content \u5f85\u7b7e\u53d1\u8bb8\u53ef\u8bc1\u6b63\u6587
     * @throws LicenseValidationException \u8bb8\u53ef\u8bc1\u6b63\u6587\u65e0\u6548\u65f6
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
     * \u6d88\u8d39\u7aef\u6269\u5c55\u6821\u9a8c\u94a9\u5b50\u3002\u5f53\u524d\u65e0\u989d\u5916\u786c\u4ef6\u7ea6\u675f\u3002
     *
     * @param content \u5f85\u6821\u9a8c\u8bb8\u53ef\u8bc1\u6b63\u6587
     * @throws LicenseValidationException \u8bb8\u53ef\u8bc1\u6b63\u6587\u65e0\u6548\u65f6
     */
    protected synchronized void validate(License content) throws LicenseValidationException {
        Objects.requireNonNull(content, "content");
    }

    /**
     * \u6309\u4e3b\u9898\u548c\u7b7e\u53d1\u89c4\u5219\u6784\u9020 TrueLicense \u7b7e\u53d1\u7aef\u3002
     * @param subject \u8bb8\u53ef\u8bc1\u4e3b\u9898
     * @param keyStoreParam \u63d0\u4f9b\u7b7e\u53d1\u5bc6\u94a5\u7684\u5bc6\u94a5\u5e93\u914d\u7f6e
     * @return \u914d\u7f6e\u597d\u7684\u8bb8\u53ef\u8bc1\u7b7e\u53d1\u7aef\u7ba1\u7406\u5668
     */
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

    /**
     * \u6309\u4e3b\u9898\u3001\u6821\u9a8c\u89c4\u5219\u53ca\u504f\u597d\u5b58\u50a8\u521b\u5efa\u8bb8\u53ef\u8bc1\u6d88\u8d39\u7aef\u3002
     * @param subject \u8bb8\u53ef\u8bc1\u4e3b\u9898
     * @param keyStoreParam \u63d0\u4f9b\u9a8c\u8bc1\u516c\u94a5\u7684\u5bc6\u94a5\u5e93\u914d\u7f6e
     * @param preferencesNode \u5f53\u524d\u8bb8\u53ef\u8bc1\u72b6\u6001\u7684\u6301\u4e45\u5316\u4f4d\u7f6e
     * @return \u914d\u7f6e\u597d\u7684\u8bb8\u53ef\u8bc1\u6d88\u8d39\u7aef\u7ba1\u7406\u5668
     */
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

    /**
     * \u6838\u5bf9\u8bb8\u53ef\u8bc1\u6d88\u8d39\u8005\u7c7b\u578b\u53ca\u6570\u91cf\u4e0e\u672c\u5730\u5b58\u50a8\u6a21\u5f0f\u662f\u5426\u5339\u914d\u3002
     * @param content \u5f85\u9a8c\u8bc1\u7684\u8bb8\u53ef\u8bc1\u5185\u5bb9
     * @throws LicenseValidationException \u6d88\u8d39\u8005\u7c7b\u578b\u6216\u6570\u91cf\u4e0d\u7b26\u5408\u6d88\u8d39\u7aef\u9650\u5236
     */
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

    /**
     * \u5b89\u88c5\u5931\u8d25\u540e\u5c1d\u8bd5\u5378\u8f7d\uff0c\u628a\u6e05\u7406\u5931\u8d25\u4f5c\u4e3a\u539f\u5f02\u5e38\u7684\u6291\u5236\u5f02\u5e38\u4fdd\u7559\u3002
     * @param primary \u5f15\u53d1\u6e05\u7406\u7684\u539f\u59cb\u5b89\u88c5\u5f02\u5e38
     */
    private void cleanupFailedInstallation(Throwable primary) {
        try {
            consumer.uninstall();
        } catch (LicenseManagementException | RuntimeException | Error cleanupException) {
            primary.addSuppressed(cleanupException);
        }
    }

    /**
     * 类型说明。
     * @author ddd4j
     * @since 3.0.x
     */
    private static final class PlainMessage implements Message {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 1L;

        /** \u8bb8\u53ef\u8bc1\u6821\u9a8c\u9519\u8bef\u4f7f\u7528\u7684\u56fa\u5b9a\u6d88\u606f\u6587\u672c\u3002 */
        private final String text;

        /**
         * \u5c01\u88c5\u8bb8\u53ef\u8bc1\u6821\u9a8c\u8bca\u65ad\u7684\u6587\u672c\u3002
         * @param text \u56fa\u5b9a\u6d88\u606f\u6587\u672c
         */
        private PlainMessage(String text) {
            this.text = text;
        }

        /**
         * 返回对象的字符串表示。
         * @return 对象的字符串表示。
         */
        @Override
        public String toString() {
            return text;
        }

        /**
         * 返回对象的字符串表示。
         * @param locale locale 参数。
         * @return 对象的字符串表示。
         */
        @Override
        public String toString(Locale locale) {
            return text;
        }
    }
}
