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
import global.namespace.truelicense.api.LicenseManagementException;
import global.namespace.truelicense.api.LicenseValidationException;
import global.namespace.truelicense.api.i18n.Message;
import global.namespace.truelicense.v1.V1;
import global.namespace.truelicense.v4.V4License;
import io.ddd4j.auth.license.creator.LicenseCreator;
import io.ddd4j.auth.license.creator.LicenseCreatorParam;
import io.ddd4j.auth.license.manager.CustomLicenseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.prefs.Preferences;

import static global.namespace.fun.io.bios.BIOS.preferences;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TrueLicense V4 签发与历史 V1 消费的真实密钥回归测试。
 */
class TrueLicenseMigrationTest {

    /**
     * 用于关联测试许可证或测试账号的主题标识。
     */
    private static final String SUBJECT = "ddd4j-test-product";

    /**
     * JUnit 为本次测试分配的临时目录。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 当前许可证测试环境使用的偏好存储。
     */
    private Preferences preferences;

    /**
     * 测试执行前执行 <code>LicenseTestPreferencesFactory.isUserRoot()</code>、<code>Preferences.userRoot()</code> 完成资源准备。
     */
    @BeforeAll
    static void requiresInMemoryPreferencesBeforeAnyLicenseOperation() {
        assertTrue(LicenseTestPreferencesFactory.isUserRoot(Preferences.userRoot()),
                "Surefire must install LicenseTestPreferencesFactory before running license tests");
    }

    /**
     * 调用 <code>Files.readAllBytes()</code>、<code>licenseFile.toPath()</code>、<code>Files.write()</code> 执行当前夹具操作。
     *
     * @param licenseFile 传给 <code>Files.readAllBytes()</code> 的 <code>File</code> 输入
     * @throws Exception 准备或读取夹具文件时失败
     */
    private static void tamper(File licenseFile) throws Exception {
        byte[] bytes = Files.readAllBytes(licenseFile.toPath());
        bytes[bytes.length / 2] ^= 0x01;
        Files.write(licenseFile.toPath(), bytes);
    }

    /**
     * 核对<code>manager.install(licenseFile)</code> 抛出 <code>LicenseManagementException.class</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @param manager     传给 <code>assertThrows()</code> 的 <code>CustomLicenseManager</code> 输入
     * @param licenseFile 传给 <code>assertThrows()</code> 的 <code>File</code> 输入
     */
    private static void assertInstallationFailsAndLeavesNoLicense(CustomLicenseManager manager, File licenseFile) {
        assertThrows(LicenseManagementException.class, () -> manager.install(licenseFile));
        assertThrows(LicenseManagementException.class, manager::verify);
    }

    /**
     * 读取 <code>param</code> 供当前测试使用。
     *
     * @param support     传给 <code>param.setPrivateKeysStorePath()</code> 的 <code>LicenseTestSupport</code> 输入
     * @param licenseFile 传给 <code>param.setLicensePath()</code> 的 <code>File</code> 输入
     * @return 当前测试使用的结果，取自 <code>param</code>
     */
    private static LicenseCreatorParam creatorParam(LicenseTestSupport support, File licenseFile) {
        LicenseCreatorParam param = new LicenseCreatorParam();
        param.setSubject(SUBJECT + '-' + UUID.randomUUID());
        param.setPrivateAlias(LicenseTestSupport.ALIAS);
        param.setKeyPass(LicenseTestSupport.KEY_PASSWORD);
        param.setStorePass(LicenseTestSupport.STORE_PASSWORD);
        param.setSignatureAlgorithm(LicenseTestSupport.SIGNATURE_ALGORITHM);
        param.setLicensePath(licenseFile.getAbsolutePath());
        param.setPrivateKeysStorePath(support.privateKeyStorePath());
        param.setIssuedTime(new Date(System.currentTimeMillis() - 60_000L));
        param.setExpiryTime(new Date(System.currentTimeMillis() + 3_600_000L));
        return param;
    }

    /**
     * 读取 <code>license</code> 供当前测试使用。
     *
     * @param subject 传给 <code>V1.builder()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>license</code>
     */
    private static License validLicense(String subject) {
        License license = V1.builder().subject(subject).build().licenseFactory().license();
        Date now = new Date();
        license.setHolder(new X500Principal("CN=ddd4j-test-holder"));
        license.setIssuer(new X500Principal("CN=ddd4j-test-issuer"));
        license.setSubject(subject);
        license.setIssued(now);
        license.setNotBefore(new Date(now.getTime() - 60_000L));
        license.setNotAfter(new Date(now.getTime() + 3_600_000L));
        license.setConsumerType("user");
        license.setConsumerAmount(1);
        return license;
    }

    /**
     * 读取 <code>license</code> 供当前测试使用。
     *
     * @param subject 传给 <code>validLicense()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>license</code>
     */
    private static License expiredLicense(String subject) {
        License license = validLicense(subject);
        license.setIssued(new Date(System.currentTimeMillis() - 7_200_000L));
        license.setNotBefore(new Date(System.currentTimeMillis() - 7_200_000L));
        license.setNotAfter(new Date(System.currentTimeMillis() - 3_600_000L));
        return license;
    }

    /**
     * 创建 <code>new LicenseValidationException</code> 供当前测试路径调用。
     *
     * @param text 用于构造本次返回结果的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>new LicenseValidationException(new TestMessage(text))</code>
     */
    private static LicenseValidationException testValidationException(String text) {
        return new LicenseValidationException(new TestMessage(text));
    }

    /**
     * 测试结束后执行 <code>LicenseTestPreferencesFactory.reset()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void removeIsolatedPreferences() {
        LicenseTestPreferencesFactory.reset();
        preferences = null;
    }

    /**
     * 验证<code>consumer.install(licenseFile).getSubject()</code> 等于 <code>SUBJECT</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void storesInstallsVerifiesAndUninstallsWithRealJksKeys() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        File licenseFile = temporaryDirectory.resolve("license.lic").toFile();
        License content = validLicense(SUBJECT);

        isolatedPreferences();
        CustomLicenseManager vendor = new CustomLicenseManager(SUBJECT, support.privateKeyStoreParam(), preferences);
        vendor.store(content, licenseFile);

        CustomLicenseManager consumer = new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), preferences);
        License installed = consumer.install(licenseFile);
        assertEquals(SUBJECT, installed.getSubject());
        assertInstanceOf(V4License.class, installed);
        assertEquals(SUBJECT, new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), preferences)
                .verify().getSubject());

        consumer.uninstall();
        assertThrows(LicenseManagementException.class, consumer::verify);
    }

    /**
     * 在测试框架中执行 <code>temporaryDirectory.resolve()</code>、<code>isolatedPreferences()</code>、<code>support.privateKeyStoreParam()</code>，验证该场景。
     *
     * @throws Exception 准备或读取夹具文件时失败
     */
    @Test
    void rejectsTamperedLicenseAndLeavesNoInstalledLicense() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        File licenseFile = temporaryDirectory.resolve("tampered.lic").toFile();
        isolatedPreferences();
        new CustomLicenseManager(SUBJECT, support.privateKeyStoreParam(), preferences)
                .store(validLicense(SUBJECT), licenseFile);

        byte[] bytes = Files.readAllBytes(licenseFile.toPath());
        bytes[bytes.length / 2] ^= 0x01;
        Files.write(licenseFile.toPath(), bytes);

        assertInstallationFailsAndLeavesNoLicense(
                new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), preferences), licenseFile);
    }

    /**
     * 在测试框架中执行 <code>temporaryDirectory.resolve()</code>、<code>isolatedPreferences()</code>、<code>signer.privateKeyStoreParam()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void rejectsLicenseSignedByAnotherKeyAndLeavesNoInstalledLicense() throws Exception {
        LicenseTestSupport signer = new LicenseTestSupport(temporaryDirectory.resolve("signer"));
        LicenseTestSupport other = new LicenseTestSupport(temporaryDirectory.resolve("other"));
        File licenseFile = temporaryDirectory.resolve("wrong-key.lic").toFile();
        isolatedPreferences();
        new CustomLicenseManager(SUBJECT, signer.privateKeyStoreParam(), preferences)
                .store(validLicense(SUBJECT), licenseFile);

        assertInstallationFailsAndLeavesNoLicense(
                new CustomLicenseManager(SUBJECT, other.publicKeyStoreParam(), preferences), licenseFile);
    }

    /**
     * 在测试框架中执行 <code>temporaryDirectory.resolve()</code>、<code>isolatedPreferences()</code>、<code>support.privateKeyStoreParam()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void rejectsWrongSubjectAndCleansInstalledState() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        File licenseFile = temporaryDirectory.resolve("wrong-subject.lic").toFile();
        isolatedPreferences();
        new CustomLicenseManager(SUBJECT, support.privateKeyStoreParam(), preferences)
                .store(validLicense(SUBJECT), licenseFile);

        assertInstallationFailsAndLeavesNoLicense(
                new CustomLicenseManager("another-product", support.publicKeyStoreParam(), preferences), licenseFile);
    }

    /**
     * 验证<code>manager.verify().getSubject()</code> 等于 <code>SUBJECT</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void sameManagerRejectsWrongSubjectReplacementInsteadOfUsingCachedLicense() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        CustomLicenseManager manager = new CustomLicenseManager(SUBJECT, support.samePasswordKeyStoreParam(),
                isolatedPreferences());
        File validFile = temporaryDirectory.resolve("cached-valid.lic").toFile();
        File wrongSubjectFile = temporaryDirectory.resolve("cached-wrong-subject.lic").toFile();
        manager.store(validLicense(SUBJECT), validFile);
        manager.install(validFile);
        assertEquals(SUBJECT, manager.verify().getSubject());
        manager.store(validLicense("another-product"), wrongSubjectFile);

        assertThrows(LicenseManagementException.class, () -> manager.install(wrongSubjectFile));
        assertThrows(LicenseManagementException.class, manager::verify);
    }

    /**
     * 验证<code>manager.install(futureFile)</code> 抛出 <code>LicenseManagementException.class</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void sameManagerRejectsFutureReplacementInsteadOfUsingCachedLicense() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        CustomLicenseManager manager = new CustomLicenseManager(SUBJECT, support.samePasswordKeyStoreParam(),
                isolatedPreferences());
        File validFile = temporaryDirectory.resolve("cached-valid.lic").toFile();
        File futureFile = temporaryDirectory.resolve("cached-future.lic").toFile();
        manager.store(validLicense(SUBJECT), validFile);
        manager.install(validFile);
        License future = validLicense(SUBJECT);
        future.setNotBefore(new Date(System.currentTimeMillis() + 3_600_000L));
        future.setNotAfter(new Date(System.currentTimeMillis() + 7_200_000L));
        manager.store(future, futureFile);

        assertThrows(LicenseManagementException.class, () -> manager.install(futureFile));
        assertThrows(LicenseManagementException.class, manager::verify);
    }

    /**
     * 验证<code>manager.install(licenseFile)</code> 抛出 <code>IllegalArgumentException.class</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void rejectsWrongExplicitSignatureAlgorithmAndLeavesNoInstalledLicense() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        File licenseFile = temporaryDirectory.resolve("wrong-algorithm.lic").toFile();
        isolatedPreferences();
        new CustomLicenseManager(SUBJECT, support.privateKeyStoreParam(), preferences)
                .store(validLicense(SUBJECT), licenseFile);
        CustomKeyStoreParam wrongAlgorithm = new CustomKeyStoreParam(TrueLicenseMigrationTest.class,
                support.publicKeyStorePath(), LicenseTestSupport.ALIAS, LicenseTestSupport.STORE_PASSWORD, null,
                "SHA1withDSA");

        CustomLicenseManager manager = new CustomLicenseManager(SUBJECT, wrongAlgorithm, preferences);
        assertThrows(IllegalArgumentException.class, () -> manager.install(licenseFile));
        assertThrows(LicenseManagementException.class, manager::verify);
    }

    /**
     * 在测试框架中执行 <code>temporaryDirectory.resolve()</code>、<code>expiredLicense()</code>、<code>isolatedPreferences()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void rejectsExpiredLicenseAndCleansInstalledState() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        File licenseFile = temporaryDirectory.resolve("expired.lic").toFile();
        License content = expiredLicense(SUBJECT);
        isolatedPreferences();
        new ExpiredFixtureManager(SUBJECT, support.privateKeyStoreParam(), preferences).store(content, licenseFile);

        assertInstallationFailsAndLeavesNoLicense(
                new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), preferences), licenseFile);
    }

    /**
     * 验证<code>manager.install(legacyFile)</code> 抛出 <code>LicenseManagementException.class</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void directManagerInvalidatesAfterFailedInstallEvenWhenRuntimeCleanupFails() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        Preferences node = isolatedPreferences();
        CustomLicenseManager manager = new CustomLicenseManager(SUBJECT, support.samePasswordKeyStoreParam(), node);
        File validFile = temporaryDirectory.resolve("cleanup-valid.lic").toFile();
        manager.store(validLicense(SUBJECT), validFile);
        manager.install(validFile);
        URL legacyResource = TrueLicenseMigrationTest.class.getResource("/truelicense-legacy/legacy-v1.lic");
        File legacyFile = new File(Objects.requireNonNull(legacyResource, "legacy license fixture").toURI());
        LicenseTestPreferencesFactory.setRemoveFailureMode(
                LicenseTestPreferencesFactory.RemoveFailureMode.RUNTIME);

        LicenseManagementException failure = assertThrows(LicenseManagementException.class,
                () -> manager.install(legacyFile));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(IllegalStateException.class, failure.getSuppressed()[0].getClass());
        assertThrows(LicenseManagementException.class, manager::verify);

        LicenseTestPreferencesFactory.setRemoveFailureMode(LicenseTestPreferencesFactory.RemoveFailureMode.NONE);
        assertEquals(SUBJECT,
                new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), node).verify().getSubject());
    }

    /**
     * 验证<code>manager.install(legacyFile)</code> 抛出 <code>LicenseManagementException.class</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void directManagerPreservesCheckedInstallFailureAndSuppressesCleanupError() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        Preferences node = isolatedPreferences();
        CustomLicenseManager manager = new CustomLicenseManager(SUBJECT, support.samePasswordKeyStoreParam(), node);
        File validFile = temporaryDirectory.resolve("runtime-valid.lic").toFile();
        manager.store(validLicense(SUBJECT), validFile);
        manager.install(validFile);
        URL legacyResource = TrueLicenseMigrationTest.class.getResource("/truelicense-legacy/legacy-v1.lic");
        File legacyFile = new File(Objects.requireNonNull(legacyResource, "legacy license fixture").toURI());
        LicenseTestPreferencesFactory.setRemoveFailureMode(LicenseTestPreferencesFactory.RemoveFailureMode.ERROR);

        LicenseManagementException failure = assertThrows(LicenseManagementException.class,
                () -> manager.install(legacyFile));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(AssertionError.class, failure.getSuppressed()[0].getClass());
        assertThrows(LicenseManagementException.class, manager::verify);

        LicenseTestPreferencesFactory.setRemoveFailureMode(LicenseTestPreferencesFactory.RemoveFailureMode.NONE);
        assertEquals(SUBJECT,
                new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), node).verify().getSubject());
    }

    /**
     * 验证<code>manager.install(replacementFile)</code> 抛出 <code>IllegalStateException.class</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void directManagerPreservesRuntimeInstallFailureAndSuppressesCleanupError() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        Preferences node = isolatedPreferences();
        RuntimeRejectingManager manager = new RuntimeRejectingManager(SUBJECT,
                support.samePasswordKeyStoreParam(), node);
        File validFile = temporaryDirectory.resolve("runtime-hook-valid.lic").toFile();
        File replacementFile = temporaryDirectory.resolve("runtime-hook-replacement.lic").toFile();
        manager.store(validLicense(SUBJECT), validFile);
        manager.store(validLicense(SUBJECT), replacementFile);
        manager.install(validFile);
        manager.rejectConsumerValidation();
        LicenseTestPreferencesFactory.setRemoveFailureMode(LicenseTestPreferencesFactory.RemoveFailureMode.ERROR);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manager.install(replacementFile));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(AssertionError.class, failure.getSuppressed()[0].getClass());
        assertThrows(LicenseManagementException.class, manager::verify);

        LicenseTestPreferencesFactory.setRemoveFailureMode(LicenseTestPreferencesFactory.RemoveFailureMode.NONE);
        assertEquals(SUBJECT,
                new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), node).verify().getSubject());
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void directManagerInvalidatesBeforeUninstallEvenWhenDeletionFails() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        Preferences node = isolatedPreferences();
        CustomLicenseManager manager = new CustomLicenseManager(SUBJECT, support.samePasswordKeyStoreParam(), node);
        File validFile = temporaryDirectory.resolve("uninstall-valid.lic").toFile();
        manager.store(validLicense(SUBJECT), validFile);
        manager.install(validFile);
        LicenseTestPreferencesFactory.setRemoveFailureMode(
                LicenseTestPreferencesFactory.RemoveFailureMode.RUNTIME);

        assertThrows(IllegalStateException.class, manager::uninstall);
        assertThrows(LicenseManagementException.class, manager::verify);

        LicenseTestPreferencesFactory.setRemoveFailureMode(LicenseTestPreferencesFactory.RemoveFailureMode.NONE);
        assertEquals(SUBJECT,
                new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), node).verify().getSubject());
    }

    /**
     * 验证<code>installConsumerLicense(support, isolatedUserPreferences(), "USER", 1).getConsumerType()</code> 等于 <code>"USER"</code>，<code>installConsumerLicense(support, isolatedUserPreferences(), "server", 1)</code> 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void userPreferencesKeepLegacyConsumerConstraints() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);

        assertEquals("USER", installConsumerLicense(support, isolatedUserPreferences(), "USER", 1).getConsumerType());
        assertThrows(LicenseManagementException.class,
                () -> installConsumerLicense(support, isolatedUserPreferences(), "server", 1));
        assertThrows(LicenseManagementException.class,
                () -> installConsumerLicense(support, isolatedUserPreferences(), "user", 2));
    }

    /**
     * 验证<code>installConsumerLicense(support, isolatedSystemPreferences(), "server", 2) .getConsumerAmount()</code> 等于 <code>2</code>，<code>installConsumerLicense(support, isolatedSystemPreferences(), "server", -1)</code> 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void systemPreferencesKeepLegacyConsumerConstraints() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);

        assertEquals(2, installConsumerLicense(support, isolatedSystemPreferences(), "server", 2)
                .getConsumerAmount());
        assertThrows(LicenseManagementException.class,
                () -> installConsumerLicense(support, isolatedSystemPreferences(), "server", -1));
    }

    /**
     * 验证<code>createRejecting.store(validLicense(SUBJECT), licenseFile)</code> 抛出 <code>LicenseManagementException.class</code>，被测调用 抛出 <code>LicenseManagementException.class</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void customCreateAndConsumerValidationHooksRemainConnected() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        Preferences node = isolatedPreferences();
        File licenseFile = temporaryDirectory.resolve("custom-hook.lic").toFile();
        HookRejectingManager createRejecting = new HookRejectingManager(SUBJECT, support.privateKeyStoreParam(), node,
                true, false);
        assertThrows(LicenseManagementException.class,
                () -> createRejecting.store(validLicense(SUBJECT), licenseFile));

        new CustomLicenseManager(SUBJECT, support.privateKeyStoreParam(), node)
                .store(validLicense(SUBJECT), licenseFile);
        HookRejectingManager consumerRejecting = new HookRejectingManager(SUBJECT, support.publicKeyStoreParam(), node,
                false, true);
        assertThrows(LicenseManagementException.class, () -> consumerRejecting.install(licenseFile));
        assertThrows(LicenseManagementException.class, consumerRejecting::verify);
    }

    /**
     * 验证<code>licenseFile.isFile()</code>为真。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void allowsFutureLicenseCreationButRejectsInstallationUntilEffective() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        File licenseFile = temporaryDirectory.resolve("future.lic").toFile();
        License content = validLicense(SUBJECT);
        content.setIssued(new Date(System.currentTimeMillis() + 3_600_000L));
        content.setNotBefore(content.getIssued());
        content.setNotAfter(new Date(content.getIssued().getTime() + 3_600_000L));
        isolatedPreferences();

        new CustomLicenseManager(SUBJECT, support.privateKeyStoreParam(), preferences).store(content, licenseFile);
        assertTrue(licenseFile.isFile());
        assertInstallationFailsAndLeavesNoLicense(
                new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), preferences), licenseFile);
    }

    /**
     * 验证<code>verify.verify()</code>为真，<code>verify.verify()</code>为假。
     *
     * @throws Exception 准备或读取夹具文件时失败
     */
    @Test
    void keepsHighLevelCreatorAndVerifierLifecycle() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        File licenseFile = temporaryDirectory.resolve("high-level.lic").toFile();
        LicenseCreatorParam param = creatorParam(support, licenseFile);
        param.setLicenseExtraModel(new LicenseExtraModel());

        assertTrue(new LicenseCreator(param).generateLicense());
        LicenseVerify verify = new LicenseVerify(param.getSubject(), LicenseTestSupport.ALIAS,
                LicenseTestSupport.STORE_PASSWORD, licenseFile.getAbsolutePath(),
                support.publicKeyStorePath(), LicenseTestSupport.SIGNATURE_ALGORITHM);
        verify.installLicense();
        assertTrue(verify.verify());
        Preferences.userNodeForPackage(LicenseVerify.class).removeNode();
        assertFalse(verify.verify());
        verify.installLicense();
        assertTrue(verify.verify());
        try {
            LicenseTestPreferencesFactory.setRemoveFailure(true);
            verify.unInstallLicense();
            assertFalse(verify.verify());

            LicenseTestPreferencesFactory.setRemoveFailure(false);
            verify.installLicense();
            assertTrue(verify.verify());
            byte[] bytes = Files.readAllBytes(licenseFile.toPath());
            bytes[bytes.length / 2] ^= 0x01;
            Files.write(licenseFile.toPath(), bytes);

            LicenseTestPreferencesFactory.setRemoveFailure(true);
            verify.installLicense();
            assertFalse(verify.verify());
        } finally {
            LicenseTestPreferencesFactory.setRemoveFailure(false);
        }
    }

    /**
     * 验证<code>param.toString().contains("sensitive-store-password")</code>为假，<code>param.toString().contains("sensitive-key-password")</code>为假。
     */
    @Test
    void licenseCreatorParamDoesNotExposePasswordsInToString() {
        LicenseCreatorParam param = new LicenseCreatorParam();
        param.setSubject(SUBJECT);
        param.setStorePass("sensitive-store-password");
        param.setKeyPass("sensitive-key-password");

        assertFalse(param.toString().contains("sensitive-store-password"));
        assertFalse(param.toString().contains("sensitive-key-password"));
    }

    /**
     * 验证<code>keyStoreParam.getSignatureAlgorithm()</code> 等于 <code>"SHA1withDSA"</code>，被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void legacyFiveArgumentApiDefaultsToSha1WithDsa() {
        CustomKeyStoreParam keyStoreParam = new CustomKeyStoreParam(TrueLicenseMigrationTest.class,
                "synthetic.jks", "alias", "store-password", null);

        assertEquals("SHA1withDSA", keyStoreParam.getSignatureAlgorithm());
        assertEquals("SHA1withDSA", new LicenseCreatorParam().getSignatureAlgorithm());
        assertEquals("SHA1withDSA", new LicenseProperties().getSignatureAlgorithm());
        assertThrows(IllegalArgumentException.class, () -> new CustomKeyStoreParam(TrueLicenseMigrationTest.class,
                "synthetic.jks", "alias", "store-password", null, (String) null));
        assertThrows(IllegalArgumentException.class, () -> new CustomKeyStoreParam(TrueLicenseMigrationTest.class,
                "synthetic.jks", "alias", "store-password", null, "  "));
    }

    /**
     * 验证<code>manager.install(licenseFile).getSubject()</code> 等于 <code>"ddd4j-legacy-fixture"</code>，<code>manager.verify().getSubject()</code> 等于 <code>"ddd4j-legacy-fixture"</code>。
     *
     * @throws Exception 向测试存储读写数据或校验其结果时失败
     */
    @Test
    void installsSyntheticLicenseGeneratedByTrueLicense133() throws Exception {
        URL licenseResource = TrueLicenseMigrationTest.class.getResource("/truelicense-legacy/legacy-v1.lic");
        URL publicStoreResource = TrueLicenseMigrationTest.class.getResource("/truelicense-legacy/legacy-public.jks");
        File licenseFile = new File(Objects.requireNonNull(licenseResource, "legacy license fixture").toURI());
        File publicStore = new File(Objects.requireNonNull(publicStoreResource, "legacy public store fixture").toURI());
        isolatedPreferences();

        CustomKeyStoreParam keyStoreParam = new CustomKeyStoreParam(TrueLicenseMigrationTest.class,
                publicStore.getAbsolutePath(), "ddd4j-legacy", "ddd4j-legacy-store-password", null);
        CustomLicenseManager manager = new CustomLicenseManager("ddd4j-legacy-fixture", keyStoreParam, preferences);

        assertEquals("ddd4j-legacy-fixture", manager.install(licenseFile).getSubject());
        assertEquals("ddd4j-legacy-fixture", manager.verify().getSubject());
        manager.uninstall();
        // 在旧版使用的偏好键中只保存原始 V1 文件，模拟升级前已经安装的许可证。
        preferences(preferences, "ddd4j-legacy-fixture").content(Files.readAllBytes(licenseFile.toPath()));

        CustomLicenseManager restarted = new CustomLicenseManager("ddd4j-legacy-fixture", keyStoreParam, preferences);
        assertEquals("ddd4j-legacy-fixture", restarted.verify().getSubject());
        restarted.uninstall();
        assertThrows(LicenseManagementException.class, restarted::verify);
        assertThrows(LicenseManagementException.class,
                () -> new CustomLicenseManager("ddd4j-legacy-fixture", keyStoreParam, preferences).verify());

        File tampered = Files.copy(licenseFile.toPath(), temporaryDirectory.resolve("tampered-legacy.lic")).toFile();
        tamper(tampered);
        assertInstallationFailsAndLeavesNoLicense(restarted, tampered);
    }

    /**
     * 通过 <code>isolatedUserPreferences()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>isolatedUserPreferences()</code>
     */
    private Preferences isolatedPreferences() {
        return isolatedUserPreferences();
    }

    /**
     * 读取 <code>preferences</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>preferences</code>
     */
    private Preferences isolatedUserPreferences() {
        preferences = Preferences.userRoot().node("/io/ddd4j/auth/license/test/" + UUID.randomUUID());
        return preferences;
    }

    /**
     * 读取 <code>preferences</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>preferences</code>
     */
    private Preferences isolatedSystemPreferences() {
        preferences = Preferences.systemRoot().node("/io/ddd4j/auth/license/test/" + UUID.randomUUID());
        return preferences;
    }

    /**
     * 通过 <code>manager.install()</code> 取得当前测试需要的结果。
     *
     * @param support        传给 <code>support.samePasswordKeyStoreParam()</code> 的 <code>LicenseTestSupport</code> 输入
     * @param node           本次调用使用的 <code>Preferences</code> 参数 <code>node</code>
     * @param consumerType   传给 <code>content.setConsumerType()</code> 的 <code>String</code> 输入
     * @param consumerAmount 传给 <code>content.setConsumerAmount()</code> 的 <code>int</code> 输入
     * @return 当前测试使用的结果，取自 <code>manager.install(licenseFile)</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private License installConsumerLicense(LicenseTestSupport support, Preferences node, String consumerType,
                                           int consumerAmount) throws Exception {
        File licenseFile = temporaryDirectory.resolve("consumer-" + UUID.randomUUID() + ".lic").toFile();
        License content = validLicense(SUBJECT);
        content.setConsumerType(consumerType);
        content.setConsumerAmount(consumerAmount);
        CustomLicenseManager manager = new CustomLicenseManager(SUBJECT, support.samePasswordKeyStoreParam(), node);
        manager.store(content, licenseFile);
        return manager.install(licenseFile);
    }

    /**
     * 实现 <code>CustomLicenseManager</code>，供测试调用 <code>validateCreate()</code> 并观察结果。
     */
    private static final class ExpiredFixtureManager extends CustomLicenseManager {

        /**
         * 使用 <code>super()</code> 初始化 <code>ExpiredFixtureManager</code> 测试夹具。
         *
         * @param subject       用于初始化 <code>ExpiredFixtureManager</code> 的 <code>String</code> 值
         * @param keyStoreParam 用于初始化 <code>ExpiredFixtureManager</code> 的 <code>CustomKeyStoreParam</code> 值
         * @param preferences   用于初始化 <code>ExpiredFixtureManager</code> 的 <code>Preferences</code> 值
         */
        private ExpiredFixtureManager(String subject, CustomKeyStoreParam keyStoreParam, Preferences preferences) {
            super(subject, keyStoreParam, preferences);
        }

        /**
         * 检查待创建记录是否满足创建条件。
         *
         * @param content 待签发许可证正文
         */
        @Override
        protected synchronized void validateCreate(License content) {
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * 实现 <code>CustomLicenseManager</code>，供测试调用 <code>validateCreate()</code>、<code>validate()</code> 并观察结果。
     */
    private static final class HookRejectingManager extends CustomLicenseManager {

        /**
         * 用于令消费者端校验路径拒绝当前许可证的测试控制项。
         */
        private final boolean rejectConsumer;
        /**
         * 用于令签发校验路径拒绝创建当前许可证的测试控制项。
         */
        private final boolean rejectCreate;

        /**
         * 使用 <code>super()</code> 初始化 <code>HookRejectingManager</code> 测试夹具。
         *
         * @param subject        用于初始化 <code>HookRejectingManager</code> 的 <code>String</code> 值
         * @param keyStoreParam  用于初始化 <code>HookRejectingManager</code> 的 <code>CustomKeyStoreParam</code> 值
         * @param preferences    用于初始化 <code>HookRejectingManager</code> 的 <code>Preferences</code> 值
         * @param rejectCreate   用于初始化 <code>HookRejectingManager</code> 的 <code>boolean</code> 值
         * @param rejectConsumer 用于初始化 <code>HookRejectingManager</code> 的 <code>boolean</code> 值
         */
        private HookRejectingManager(String subject, CustomKeyStoreParam keyStoreParam, Preferences preferences,
                                     boolean rejectCreate, boolean rejectConsumer) {
            super(subject, keyStoreParam, preferences);
            this.rejectCreate = rejectCreate;
            this.rejectConsumer = rejectConsumer;
        }

        /**
         * 检查待创建记录是否满足创建条件。
         *
         * <p>测试夹具会调用 <code>super.validateCreate(content)</code>、<code>testValidationException("synthetic create hook rejection")</code>。</p>
         *
         * @param content 待签发许可证正文
         * @throws LicenseValidationException 当前操作无法正常完成时
         */
        @Override
        protected synchronized void validateCreate(License content) throws LicenseValidationException {
            super.validateCreate(content);
            if (rejectCreate) {
                throw testValidationException("synthetic create hook rejection");
            }
        }

        /**
         * 校验当前调用提交的数据。
         *
         * <p>测试夹具会调用 <code>super.validate(content)</code>、<code>testValidationException("synthetic consumer hook rejection")</code>。</p>
         *
         * @param content 待校验许可证正文
         * @throws LicenseValidationException 当前操作无法正常完成时
         */
        @Override
        protected synchronized void validate(License content) throws LicenseValidationException {
            super.validate(content);
            if (rejectConsumer) {
                throw testValidationException("synthetic consumer hook rejection");
            }
        }
    }

    /**
     * 实现 <code>CustomLicenseManager</code>，供测试调用 <code>rejectConsumerValidation()</code>、<code>validate()</code> 并观察结果。
     */
    private static final class RuntimeRejectingManager extends CustomLicenseManager {

        /**
         * 用于令消费者端校验路径拒绝当前许可证的测试控制项。
         */
        private boolean rejectConsumer;

        /**
         * 使用 <code>super()</code> 初始化 <code>RuntimeRejectingManager</code> 测试夹具。
         *
         * @param subject       用于初始化 <code>RuntimeRejectingManager</code> 的 <code>String</code> 值
         * @param keyStoreParam 用于初始化 <code>RuntimeRejectingManager</code> 的 <code>CustomKeyStoreParam</code> 值
         * @param preferences   用于初始化 <code>RuntimeRejectingManager</code> 的 <code>Preferences</code> 值
         */
        private RuntimeRejectingManager(String subject, CustomKeyStoreParam keyStoreParam, Preferences preferences) {
            super(subject, keyStoreParam, preferences);
        }

        /**
         * 根据本次调用更新 <code>rejectConsumer = true</code>，用于后续测试步骤。
         */
        private void rejectConsumerValidation() {
            rejectConsumer = true;
        }

        /**
         * 校验当前调用提交的数据。
         *
         * <p>测试夹具会调用 <code>super.validate(content)</code>。</p>
         *
         * @param content 待校验许可证正文
         * @throws LicenseValidationException 当前操作无法正常完成时
         */
        @Override
        protected synchronized void validate(License content) throws LicenseValidationException {
            super.validate(content);
            if (rejectConsumer) {
                throw new IllegalStateException("synthetic runtime validation rejection");
            }
        }
    }

    /**
     * 提供代理消息协议信息和负载的测试样本。
     */
    private static final class TestMessage implements Message {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 测试内容映射或响应比较使用的文本。
         */
        private final String text;

        /**
         * 设置 <code>text</code>，用于构造 <code>TestMessage</code> 测试实例。
         *
         * @param text 用于初始化 <code>TestMessage</code> 的 <code>String</code> 值
         */
        private TestMessage(String text) {
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
         * 读取 <code>text</code> 供当前测试使用。
         *
         * @param locale 本次调用使用的 <code>Locale</code> 参数 <code>locale</code>
         * @return 当前测试使用的结果，取自 <code>text</code>
         */
        @Override
        public String toString(Locale locale) {
            return text;
        }
    }
}
