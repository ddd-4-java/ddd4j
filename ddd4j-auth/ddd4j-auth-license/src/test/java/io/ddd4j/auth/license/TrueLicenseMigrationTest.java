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

    /** \u7528\u4e8e\u5173\u8054\u6d4b\u8bd5\u8bb8\u53ef\u8bc1\u6216\u6d4b\u8bd5\u8d26\u53f7\u7684\u4e3b\u9898\u6807\u8bc6\u3002 */
    private static final String SUBJECT = "ddd4j-test-product";

    /** JUnit \u4e3a\u672c\u6b21\u6d4b\u8bd5\u5206\u914d\u7684\u4e34\u65f6\u76ee\u5f55\u3002 */
    @TempDir
    Path temporaryDirectory;

    /** \u5f53\u524d\u8bb8\u53ef\u8bc1\u6d4b\u8bd5\u73af\u5883\u4f7f\u7528\u7684\u504f\u597d\u5b58\u50a8\u3002 */
    private Preferences preferences;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>LicenseTestPreferencesFactory.isUserRoot()</code>\u3001<code>Preferences.userRoot()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeAll
    static void requiresInMemoryPreferencesBeforeAnyLicenseOperation() {
        assertTrue(LicenseTestPreferencesFactory.isUserRoot(Preferences.userRoot()),
                "Surefire must install LicenseTestPreferencesFactory before running license tests");
    }

    /**
     * \u8c03\u7528 <code>Files.readAllBytes()</code>\u3001<code>licenseFile.toPath()</code>\u3001<code>Files.write()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param licenseFile \u4f20\u7ed9 <code>Files.readAllBytes()</code> \u7684 <code>File</code> \u8f93\u5165
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    private static void tamper(File licenseFile) throws Exception {
        byte[] bytes = Files.readAllBytes(licenseFile.toPath());
        bytes[bytes.length / 2] ^= 0x01;
        Files.write(licenseFile.toPath(), bytes);
    }

    /**
     * \u6838\u5bf9<code>manager.install(licenseFile)</code> \u629b\u51fa <code>LicenseManagementException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @param manager \u4f20\u7ed9 <code>assertThrows()</code> \u7684 <code>CustomLicenseManager</code> \u8f93\u5165
     * @param licenseFile \u4f20\u7ed9 <code>assertThrows()</code> \u7684 <code>File</code> \u8f93\u5165
     */
    private static void assertInstallationFailsAndLeavesNoLicense(CustomLicenseManager manager, File licenseFile) {
        assertThrows(LicenseManagementException.class, () -> manager.install(licenseFile));
        assertThrows(LicenseManagementException.class, manager::verify);
    }

    /**
     * \u8bfb\u53d6 <code>param</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param support \u4f20\u7ed9 <code>param.setPrivateKeysStorePath()</code> \u7684 <code>LicenseTestSupport</code> \u8f93\u5165
     * @param licenseFile \u4f20\u7ed9 <code>param.setLicensePath()</code> \u7684 <code>File</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>param</code>
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
     * \u8bfb\u53d6 <code>license</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param subject \u4f20\u7ed9 <code>V1.builder()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>license</code>
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
     * \u8bfb\u53d6 <code>license</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param subject \u4f20\u7ed9 <code>validLicense()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>license</code>
     */
    private static License expiredLicense(String subject) {
        License license = validLicense(subject);
        license.setIssued(new Date(System.currentTimeMillis() - 7_200_000L));
        license.setNotBefore(new Date(System.currentTimeMillis() - 7_200_000L));
        license.setNotAfter(new Date(System.currentTimeMillis() - 3_600_000L));
        return license;
    }

    /**
     * \u521b\u5efa <code>new LicenseValidationException</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param text \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new LicenseValidationException(new TestMessage(text))</code>
     */
    private static LicenseValidationException testValidationException(String text) {
        return new LicenseValidationException(new TestMessage(text));
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>LicenseTestPreferencesFactory.reset()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void removeIsolatedPreferences() {
        LicenseTestPreferencesFactory.reset();
        preferences = null;
    }

    /**
     * \u9a8c\u8bc1<code>consumer.install(licenseFile).getSubject()</code> \u7b49\u4e8e <code>SUBJECT</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>temporaryDirectory.resolve()</code>\u3001<code>isolatedPreferences()</code>\u3001<code>support.privateKeyStoreParam()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
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
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>temporaryDirectory.resolve()</code>\u3001<code>isolatedPreferences()</code>\u3001<code>signer.privateKeyStoreParam()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>temporaryDirectory.resolve()</code>\u3001<code>isolatedPreferences()</code>\u3001<code>support.privateKeyStoreParam()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>manager.verify().getSubject()</code> \u7b49\u4e8e <code>SUBJECT</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>manager.install(futureFile)</code> \u629b\u51fa <code>LicenseManagementException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>manager.install(licenseFile)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>temporaryDirectory.resolve()</code>\u3001<code>expiredLicense()</code>\u3001<code>isolatedPreferences()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>manager.install(legacyFile)</code> \u629b\u51fa <code>LicenseManagementException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>manager.install(legacyFile)</code> \u629b\u51fa <code>LicenseManagementException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>manager.install(replacementFile)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>installConsumerLicense(support, isolatedUserPreferences(), "USER", 1).getConsumerType()</code> \u7b49\u4e8e <code>"USER"</code>\uff0c<code>installConsumerLicense(support, isolatedUserPreferences(), "server", 1)</code> \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>installConsumerLicense(support, isolatedSystemPreferences(), "server", 2) .getConsumerAmount()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>installConsumerLicense(support, isolatedSystemPreferences(), "server", -1)</code> \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>createRejecting.store(validLicense(SUBJECT), licenseFile)</code> \u629b\u51fa <code>LicenseManagementException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LicenseManagementException.class</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>licenseFile.isFile()</code>\u4e3a\u771f\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>verify.verify()</code>\u4e3a\u771f\uff0c<code>verify.verify()</code>\u4e3a\u5047\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
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

    /** \u9a8c\u8bc1<code>param.toString().contains("sensitive-store-password")</code>\u4e3a\u5047\uff0c<code>param.toString().contains("sensitive-key-password")</code>\u4e3a\u5047\u3002 */
    @Test
    void licenseCreatorParamDoesNotExposePasswordsInToString() {
        LicenseCreatorParam param = new LicenseCreatorParam();
        param.setSubject(SUBJECT);
        param.setStorePass("sensitive-store-password");
        param.setKeyPass("sensitive-key-password");

        assertFalse(param.toString().contains("sensitive-store-password"));
        assertFalse(param.toString().contains("sensitive-key-password"));
    }

    /** \u9a8c\u8bc1<code>keyStoreParam.getSignatureAlgorithm()</code> \u7b49\u4e8e <code>"SHA1withDSA"</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
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
     * \u9a8c\u8bc1<code>manager.install(licenseFile).getSubject()</code> \u7b49\u4e8e <code>"ddd4j-legacy-fixture"</code>\uff0c<code>manager.verify().getSubject()</code> \u7b49\u4e8e <code>"ddd4j-legacy-fixture"</code>\u3002
     * @throws Exception \u5411\u6d4b\u8bd5\u5b58\u50a8\u8bfb\u5199\u6570\u636e\u6216\u6821\u9a8c\u5176\u7ed3\u679c\u65f6\u5931\u8d25
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
     * \u901a\u8fc7 <code>isolatedUserPreferences()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>isolatedUserPreferences()</code>
     */
    private Preferences isolatedPreferences() {
        return isolatedUserPreferences();
    }

    /**
     * \u8bfb\u53d6 <code>preferences</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>preferences</code>
     */
    private Preferences isolatedUserPreferences() {
        preferences = Preferences.userRoot().node("/io/ddd4j/auth/license/test/" + UUID.randomUUID());
        return preferences;
    }

    /**
     * \u8bfb\u53d6 <code>preferences</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>preferences</code>
     */
    private Preferences isolatedSystemPreferences() {
        preferences = Preferences.systemRoot().node("/io/ddd4j/auth/license/test/" + UUID.randomUUID());
        return preferences;
    }

    /**
     * \u901a\u8fc7 <code>manager.install()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param support \u4f20\u7ed9 <code>support.samePasswordKeyStoreParam()</code> \u7684 <code>LicenseTestSupport</code> \u8f93\u5165
     * @param node \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Preferences</code> \u53c2\u6570 <code>node</code>
     * @param consumerType \u4f20\u7ed9 <code>content.setConsumerType()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param consumerAmount \u4f20\u7ed9 <code>content.setConsumerAmount()</code> \u7684 <code>int</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>manager.install(licenseFile)</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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

    /** \u5b9e\u73b0 <code>CustomLicenseManager</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>validateCreate()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class ExpiredFixtureManager extends CustomLicenseManager {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>ExpiredFixtureManager</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param subject \u7528\u4e8e\u521d\u59cb\u5316 <code>ExpiredFixtureManager</code> \u7684 <code>String</code> \u503c
         * @param keyStoreParam \u7528\u4e8e\u521d\u59cb\u5316 <code>ExpiredFixtureManager</code> \u7684 <code>CustomKeyStoreParam</code> \u503c
         * @param preferences \u7528\u4e8e\u521d\u59cb\u5316 <code>ExpiredFixtureManager</code> \u7684 <code>Preferences</code> \u503c
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

    /** \u5b9e\u73b0 <code>CustomLicenseManager</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>validateCreate()</code>\u3001<code>validate()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class HookRejectingManager extends CustomLicenseManager {

        /** \u7528\u4e8e\u4ee4\u6d88\u8d39\u8005\u7aef\u6821\u9a8c\u8def\u5f84\u62d2\u7edd\u5f53\u524d\u8bb8\u53ef\u8bc1\u7684\u6d4b\u8bd5\u63a7\u5236\u9879\u3002 */
        private final boolean rejectConsumer;
        /** \u7528\u4e8e\u4ee4\u7b7e\u53d1\u6821\u9a8c\u8def\u5f84\u62d2\u7edd\u521b\u5efa\u5f53\u524d\u8bb8\u53ef\u8bc1\u7684\u6d4b\u8bd5\u63a7\u5236\u9879\u3002 */
        private final boolean rejectCreate;

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>HookRejectingManager</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param subject \u7528\u4e8e\u521d\u59cb\u5316 <code>HookRejectingManager</code> \u7684 <code>String</code> \u503c
         * @param keyStoreParam \u7528\u4e8e\u521d\u59cb\u5316 <code>HookRejectingManager</code> \u7684 <code>CustomKeyStoreParam</code> \u503c
         * @param preferences \u7528\u4e8e\u521d\u59cb\u5316 <code>HookRejectingManager</code> \u7684 <code>Preferences</code> \u503c
         * @param rejectCreate \u7528\u4e8e\u521d\u59cb\u5316 <code>HookRejectingManager</code> \u7684 <code>boolean</code> \u503c
         * @param rejectConsumer \u7528\u4e8e\u521d\u59cb\u5316 <code>HookRejectingManager</code> \u7684 <code>boolean</code> \u503c
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

    /** \u5b9e\u73b0 <code>CustomLicenseManager</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>rejectConsumerValidation()</code>\u3001<code>validate()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class RuntimeRejectingManager extends CustomLicenseManager {

        /** \u7528\u4e8e\u4ee4\u6d88\u8d39\u8005\u7aef\u6821\u9a8c\u8def\u5f84\u62d2\u7edd\u5f53\u524d\u8bb8\u53ef\u8bc1\u7684\u6d4b\u8bd5\u63a7\u5236\u9879\u3002 */
        private boolean rejectConsumer;

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>RuntimeRejectingManager</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param subject \u7528\u4e8e\u521d\u59cb\u5316 <code>RuntimeRejectingManager</code> \u7684 <code>String</code> \u503c
         * @param keyStoreParam \u7528\u4e8e\u521d\u59cb\u5316 <code>RuntimeRejectingManager</code> \u7684 <code>CustomKeyStoreParam</code> \u503c
         * @param preferences \u7528\u4e8e\u521d\u59cb\u5316 <code>RuntimeRejectingManager</code> \u7684 <code>Preferences</code> \u503c
         */
        private RuntimeRejectingManager(String subject, CustomKeyStoreParam keyStoreParam, Preferences preferences) {
            super(subject, keyStoreParam, preferences);
        }

        /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>rejectConsumer = true</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
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

    /** \u63d0\u4f9b\u4ee3\u7406\u6d88\u606f\u534f\u8bae\u4fe1\u606f\u548c\u8d1f\u8f7d\u7684\u6d4b\u8bd5\u6837\u672c\u3002 */
    private static final class TestMessage implements Message {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 1L;

        /** \u6d4b\u8bd5\u5185\u5bb9\u6620\u5c04\u6216\u54cd\u5e94\u6bd4\u8f83\u4f7f\u7528\u7684\u6587\u672c\u3002 */
        private final String text;

        /**
         * \u8bbe\u7f6e <code>text</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestMessage</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param text \u7528\u4e8e\u521d\u59cb\u5316 <code>TestMessage</code> \u7684 <code>String</code> \u503c
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
         * \u8bfb\u53d6 <code>text</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param locale \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Locale</code> \u53c2\u6570 <code>locale</code>
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>text</code>
         */
        @Override
        public String toString(Locale locale) {
            return text;
        }
    }
}
