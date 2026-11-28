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

    /**
     * 用于许可证安装、卸载和在线验证的 TrueLicense 消费端管理器。
     */
    private final ConsumerLicenseManager consumer;
    /**
     * 是否使用用户级偏好存储保存许可证。
     */
    private final boolean userPreferences;
    /**
     * 负责按创建约束签发许可证的 TrueLicense 管理器。
     */
    private final VendorLicenseManager vendor;
    /**
     * 记录安装或验证失败后当前管理器的验证状态是否已经失效。
     */
    private boolean verificationInvalidated;

    /**
     * 使用许可证主题、密钥库和存储节点创建管理器。
     *
     * @param subject       许可证主题
     * @param keyStoreParam 密钥库参数
     * @param preferences   许可证存储节点
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
     * 把已有密钥库输入包装为 TrueLicense 可按需读取的源。
     *
     * @param keyStoreParam 提供密钥库输入的配置
     * @return 由密钥库配置打开输入流的许可证资源源
     */
    private static Source keyStoreSource(CustomKeyStoreParam keyStoreParam) {
        return () -> keyStoreParam::getStream;
    }

    /**
     * 构造拒绝对已失效管理器继续执行验证的异常。
     *
     * @return 封装验证状态失效原因的管理异常
     */
    private static LicenseManagementException invalidatedException() {
        return new LicenseManagementException(
                new IllegalStateException("license manager verification state has been invalidated"));
    }

    /**
     * 把现有口令转换为 TrueLicense 可关闭的字符数组保护对象。
     *
     * @param value 密钥库或密钥条目的口令
     * @return 在关闭时清空字符数组的口令保护对象
     */
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

    /**
     * 将许可证校验失败信息转换成 TrueLicense 校验异常。
     *
     * @param text 诊断所用的纯文本消息
     * @return 带原始消息的许可证校验异常
     */
    private static LicenseValidationException validationException(String text) {
        return new LicenseValidationException(new PlainMessage(text));
    }

    /**
     * 要求必填许可证配置包含非空白字符。
     *
     * @param value 待校验的值
     * @param name  用于定位错误的配置名称
     * @throws IllegalArgumentException 配置缺失或只含空白字符
     */
    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /**
     * 创建与当前签发端格式一致的空许可证正文。
     *
     * @return 由签发端上下文的许可证工厂创建的可填充正文
     */
    public License newLicense() {
        return vendor.context().licenseFactory().license();
    }

    /**
     * 生成许可证并写入指定文件。
     *
     * @param content 许可证正文
     * @param target  输出文件
     * @throws LicenseManagementException 生成或写入失败时
     */
    public synchronized void store(License content, File target) throws LicenseManagementException {
        vendor.generateKeyFrom(Objects.requireNonNull(content, "content"))
                .saveTo(file(Objects.requireNonNull(target, "target")));
    }

    /**
     * 安装许可证，随后立即执行业务校验并加载许可证正文。
     *
     * @param source 许可证文件
     * @return 已安装的许可证正文
     * @throws LicenseManagementException 安装或校验失败时
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
     *
     * @return 已安装的许可证正文
     * @throws LicenseManagementException 校验或读取失败时
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
     *
     * @throws LicenseManagementException 卸载失败时
     */
    public synchronized void uninstall() throws LicenseManagementException {
        verificationInvalidated = true;
        consumer.uninstall();
    }

    /**
     * 保留旧生成端的时间约束：允许签发尚未生效的许可证，但拒绝已过期或反向时间区间。
     *
     * @param content 待签发许可证正文
     * @throws LicenseValidationException 许可证正文无效时
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
     *
     * @param content 待校验许可证正文
     * @throws LicenseValidationException 许可证正文无效时
     */
    protected synchronized void validate(License content) throws LicenseValidationException {
        Objects.requireNonNull(content, "content");
    }

    /**
     * 按主题和签发规则构造 TrueLicense 签发端。
     *
     * @param subject       许可证主题
     * @param keyStoreParam 提供签发密钥的密钥库配置
     * @return 配置好的许可证签发端管理器
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
     * 按主题、校验规则及偏好存储创建许可证消费端。
     *
     * @param subject         许可证主题
     * @param keyStoreParam   提供验证公钥的密钥库配置
     * @param preferencesNode 当前许可证状态的持久化位置
     * @return 配置好的许可证消费端管理器
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
     * 核对许可证消费者类型及数量与本地存储模式是否匹配。
     *
     * @param content 待验证的许可证内容
     * @throws LicenseValidationException 消费者类型或数量不符合消费端限制
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
     * 安装失败后尝试卸载，把清理失败作为原异常的抑制异常保留。
     *
     * @param primary 引发清理的原始安装异常
     */
    private void cleanupFailedInstallation(Throwable primary) {
        try {
            consumer.uninstall();
        } catch (LicenseManagementException | RuntimeException | Error cleanupException) {
            primary.addSuppressed(cleanupException);
        }
    }

    /**
     * 保存许可证管理回调所需的纯文本消息。
     */
    private static final class PlainMessage implements Message {

        /**
         * Java 序列化版本标识。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 许可证校验错误使用的固定消息文本。
         */
        private final String text;

        /**
         * 封装许可证校验诊断的文本。
         *
         * @param text 固定消息文本
         */
        private PlainMessage(String text) {
            this.text = text;
        }

        /**
         * 生成用于调试和日志的对象描述。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return text;
        }

        /**
         * 返回校验消息的固定文本。
         *
         * @param locale 调用方指定的显示区域
         * @return 构造时提供的消息文本
         */
        @Override
        public String toString(Locale locale) {
            return text;
        }
    }
}
