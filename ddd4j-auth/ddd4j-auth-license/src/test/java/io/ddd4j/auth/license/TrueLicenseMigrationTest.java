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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TrueLicense 4.x V1 格式迁移的真实密码学回归测试。
 */
class TrueLicenseMigrationTest {

    private static final String SUBJECT = "ddd4j-test-product";

    @TempDir
    Path temporaryDirectory;

    private Preferences preferences;

    @BeforeAll
    static void requiresInMemoryPreferencesBeforeAnyLicenseOperation() {
        assertTrue(LicenseTestPreferencesFactory.isUserRoot(Preferences.userRoot()),
                "Surefire must install LicenseTestPreferencesFactory before running license tests");
    }

    @AfterEach
    void removeIsolatedPreferences() {
        LicenseTestPreferencesFactory.reset();
        preferences = null;
    }

    @Test
    void storesInstallsVerifiesAndUninstallsWithRealJksKeys() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        File licenseFile = temporaryDirectory.resolve("license.lic").toFile();
        License content = validLicense(SUBJECT);

        isolatedPreferences();
        CustomLicenseManager vendor = new CustomLicenseManager(SUBJECT, support.privateKeyStoreParam(), preferences);
        vendor.store(content, licenseFile);

        CustomLicenseManager consumer = new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), preferences);
        assertEquals(SUBJECT, consumer.install(licenseFile).getSubject());
        assertEquals(SUBJECT, consumer.verify().getSubject());

        consumer.uninstall();
        assertThrows(LicenseManagementException.class, consumer::verify);
    }

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

    @Test
    void directManagerInvalidatesAfterFailedInstallEvenWhenRuntimeCleanupFails() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        Preferences node = isolatedPreferences();
        CustomLicenseManager manager = new CustomLicenseManager(SUBJECT, support.samePasswordKeyStoreParam(), node);
        File validFile = temporaryDirectory.resolve("cleanup-valid.lic").toFile();
        File tamperedFile = temporaryDirectory.resolve("cleanup-tampered.lic").toFile();
        manager.store(validLicense(SUBJECT), validFile);
        manager.store(validLicense(SUBJECT), tamperedFile);
        manager.install(validFile);
        tamper(tamperedFile);
        LicenseTestPreferencesFactory.setRemoveFailureMode(
                LicenseTestPreferencesFactory.RemoveFailureMode.RUNTIME);

        LicenseManagementException failure = assertThrows(LicenseManagementException.class,
                () -> manager.install(tamperedFile));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(IllegalStateException.class, failure.getSuppressed()[0].getClass());
        assertThrows(LicenseManagementException.class, manager::verify);

        LicenseTestPreferencesFactory.setRemoveFailureMode(LicenseTestPreferencesFactory.RemoveFailureMode.NONE);
        assertEquals(SUBJECT,
                new CustomLicenseManager(SUBJECT, support.publicKeyStoreParam(), node).verify().getSubject());
    }

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

    @Test
    void userPreferencesKeepLegacyConsumerConstraints() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);

        assertEquals("USER", installConsumerLicense(support, isolatedUserPreferences(), "USER", 1).getConsumerType());
        assertThrows(LicenseManagementException.class,
                () -> installConsumerLicense(support, isolatedUserPreferences(), "server", 1));
        assertThrows(LicenseManagementException.class,
                () -> installConsumerLicense(support, isolatedUserPreferences(), "user", 2));
    }

    @Test
    void systemPreferencesKeepLegacyConsumerConstraints() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);

        assertEquals(2, installConsumerLicense(support, isolatedSystemPreferences(), "server", 2)
                .getConsumerAmount());
        assertThrows(LicenseManagementException.class,
                () -> installConsumerLicense(support, isolatedSystemPreferences(), "server", -1));
    }

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

    @Test
    void keepsHighLevelCreatorAndVerifierLifecycle() throws Exception {
        LicenseTestSupport support = new LicenseTestSupport(temporaryDirectory);
        File licenseFile = temporaryDirectory.resolve("high-level.lic").toFile();
        LicenseCreatorParam param = creatorParam(support, licenseFile);

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

    @Test
    void licenseCreatorParamDoesNotExposePasswordsInToString() {
        LicenseCreatorParam param = new LicenseCreatorParam();
        param.setSubject(SUBJECT);
        param.setStorePass("sensitive-store-password");
        param.setKeyPass("sensitive-key-password");

        assertFalse(param.toString().contains("sensitive-store-password"));
        assertFalse(param.toString().contains("sensitive-key-password"));
    }

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
    }

    private Preferences isolatedPreferences() {
        return isolatedUserPreferences();
    }

    private Preferences isolatedUserPreferences() {
        preferences = Preferences.userRoot().node("/io/ddd4j/auth/license/test/" + UUID.randomUUID());
        return preferences;
    }

    private Preferences isolatedSystemPreferences() {
        preferences = Preferences.systemRoot().node("/io/ddd4j/auth/license/test/" + UUID.randomUUID());
        return preferences;
    }

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

    private static void tamper(File licenseFile) throws Exception {
        byte[] bytes = Files.readAllBytes(licenseFile.toPath());
        bytes[bytes.length / 2] ^= 0x01;
        Files.write(licenseFile.toPath(), bytes);
    }

    private static void assertInstallationFailsAndLeavesNoLicense(CustomLicenseManager manager, File licenseFile) {
        assertThrows(LicenseManagementException.class, () -> manager.install(licenseFile));
        assertThrows(LicenseManagementException.class, manager::verify);
    }

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

    private static License expiredLicense(String subject) {
        License license = validLicense(subject);
        license.setIssued(new Date(System.currentTimeMillis() - 7_200_000L));
        license.setNotBefore(new Date(System.currentTimeMillis() - 7_200_000L));
        license.setNotAfter(new Date(System.currentTimeMillis() - 3_600_000L));
        return license;
    }

    private static LicenseValidationException testValidationException(String text) {
        return new LicenseValidationException(new TestMessage(text));
    }

    private static final class ExpiredFixtureManager extends CustomLicenseManager {

        private ExpiredFixtureManager(String subject, CustomKeyStoreParam keyStoreParam, Preferences preferences) {
            super(subject, keyStoreParam, preferences);
        }

        @Override
        protected synchronized void validateCreate(License content) {
            Objects.requireNonNull(content, "content");
        }
    }

    private static final class HookRejectingManager extends CustomLicenseManager {

        private final boolean rejectConsumer;
        private final boolean rejectCreate;

        private HookRejectingManager(String subject, CustomKeyStoreParam keyStoreParam, Preferences preferences,
                                     boolean rejectCreate, boolean rejectConsumer) {
            super(subject, keyStoreParam, preferences);
            this.rejectCreate = rejectCreate;
            this.rejectConsumer = rejectConsumer;
        }

        @Override
        protected synchronized void validateCreate(License content) throws LicenseValidationException {
            super.validateCreate(content);
            if (rejectCreate) {
                throw testValidationException("synthetic create hook rejection");
            }
        }

        @Override
        protected synchronized void validate(License content) throws LicenseValidationException {
            super.validate(content);
            if (rejectConsumer) {
                throw testValidationException("synthetic consumer hook rejection");
            }
        }
    }

    private static final class RuntimeRejectingManager extends CustomLicenseManager {

        private boolean rejectConsumer;

        private RuntimeRejectingManager(String subject, CustomKeyStoreParam keyStoreParam, Preferences preferences) {
            super(subject, keyStoreParam, preferences);
        }

        private void rejectConsumerValidation() {
            rejectConsumer = true;
        }

        @Override
        protected synchronized void validate(License content) throws LicenseValidationException {
            super.validate(content);
            if (rejectConsumer) {
                throw new IllegalStateException("synthetic runtime validation rejection");
            }
        }
    }

    private static final class TestMessage implements Message {

        private static final long serialVersionUID = 1L;

        private final String text;

        private TestMessage(String text) {
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
