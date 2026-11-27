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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TrueLicense 集成测试的真实密钥库夹具。
 */
final class LicenseTestSupport {

    /** \u6d4b\u8bd5\u5bc6\u94a5\u5e93\u5185\u7528\u4e8e\u67e5\u627e\u8bc1\u4e66\u6216\u5bc6\u94a5\u7684\u522b\u540d\u3002 */
    static final String ALIAS = "ddd4j-test";
    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
    static final String KEY_PASSWORD = "ddd4j-test-key-password";
    /** \u6d4b\u8bd5\u7b7e\u53d1\u548c\u6838\u9a8c\u8bb8\u53ef\u8bc1\u6240\u4f7f\u7528\u7684\u7b97\u6cd5\u3002 */
    static final String SIGNATURE_ALGORITHM = "SHA256withDSA";
    /** \u521b\u5efa\u548c\u8bfb\u53d6\u6d4b\u8bd5\u5bc6\u94a5\u5e93\u65f6\u4f7f\u7528\u7684\u53e3\u4ee4\u3002 */
    static final String STORE_PASSWORD = "ddd4j-test-store-password";

    /** \u7528\u4e8e\u751f\u6210\u5e76\u4fdd\u5b58\u6d4b\u8bd5\u7b7e\u53d1\u5bc6\u94a5\u7684\u5bc6\u94a5\u5e93\u3002 */
    private final Path privateKeyStore;
    /** \u7528\u4e8e\u8bfb\u53d6\u6d4b\u8bd5\u9a8c\u7b7e\u516c\u94a5\u7684\u5bc6\u94a5\u5e93\u3002 */
    private final Path publicKeyStore;
    /** \u7528\u540c\u4e00\u6d4b\u8bd5\u53e3\u4ee4\u521b\u5efa\u7684\u5bc6\u94a5\u5e93\u5939\u5177\u3002 */
    private final Path samePasswordKeyStore;

    /**
     * \u6784\u9020\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684 <code>LicenseTestSupport</code> \u5b9e\u4f8b\u3002
     * @param directory \u7528\u4e8e\u521d\u59cb\u5316 <code>LicenseTestSupport</code> \u7684 <code>Path</code> \u503c
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    LicenseTestSupport(Path directory) throws Exception {
        Files.createDirectories(directory);
        privateKeyStore = directory.resolve("private.jks");
        publicKeyStore = directory.resolve("public.jks");
        samePasswordKeyStore = directory.resolve("same-password-private.jks");
        Path certificate = directory.resolve("certificate.cer");

        runKeytool("-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "DSA",
                "-keysize", "2048",
                "-validity", "3650",
                "-dname", "CN=ddd4j-test,OU=Test,O=ddd4j,L=Shanghai,ST=Shanghai,C=CN",
                "-keystore", privateKeyStore.toString(),
                "-storetype", "JKS",
                "-storepass", STORE_PASSWORD,
                "-keypass", KEY_PASSWORD,
                "-noprompt");
        runKeytool("-exportcert",
                "-alias", ALIAS,
                "-keystore", privateKeyStore.toString(),
                "-storepass", STORE_PASSWORD,
                "-file", certificate.toString());
        runKeytool("-importcert",
                "-alias", ALIAS,
                "-keystore", publicKeyStore.toString(),
                "-storetype", "JKS",
                "-storepass", STORE_PASSWORD,
                "-file", certificate.toString(),
                "-noprompt");
        runKeytool("-importkeystore",
                "-srckeystore", privateKeyStore.toString(),
                "-srcstoretype", "JKS",
                "-srcstorepass", STORE_PASSWORD,
                "-srckeypass", KEY_PASSWORD,
                "-srcalias", ALIAS,
                "-destkeystore", samePasswordKeyStore.toString(),
                "-deststoretype", "JKS",
                "-deststorepass", STORE_PASSWORD,
                "-destkeypass", STORE_PASSWORD,
                "-destalias", ALIAS,
                "-noprompt");
    }

    /**
     * \u8c03\u7528 <code>System.getProperty()</code>\u3001<code>command.add()</code>\u3001<code>executable.getAbsolutePath()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param arguments \u4f20\u7ed9 <code>command.addAll()</code> \u7684 <code>String[]</code> \u8f93\u5165
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    private static void runKeytool(String... arguments) throws Exception {
        File executable = new File(new File(System.getProperty("java.home"), "bin"), "keytool");
        List<String> command = new ArrayList<>();
        command.add(executable.getAbsolutePath());
        command.addAll(Arrays.asList(arguments));

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = read(process.getInputStream());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("keytool failed with exit code " + exitCode + ": " + output);
        }
    }

    /**
     * \u521b\u5efa <code>new String</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param inputStream \u4f20\u7ed9 <code>inputStream.read()</code> \u7684 <code>InputStream</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new String(outputStream.toByteArray(), StandardCharsets.UTF_8)</code>
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private static String read(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = inputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, count);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * \u6784\u9020 <code>CustomKeyStoreParam</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>CustomKeyStoreParam</code> \u7ed3\u679c
     */
    CustomKeyStoreParam privateKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseTestSupport.class, privateKeyStore.toString(), ALIAS,
                STORE_PASSWORD, KEY_PASSWORD, SIGNATURE_ALGORITHM);
    }

    /**
     * \u521b\u5efa <code>new CustomKeyStoreParam</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new CustomKeyStoreParam(LicenseTestSupport.class, publicKeyStore.toString(), ALIAS, STORE_PASSWORD, null, SIGNATURE_ALGORITHM)</code>
     */
    CustomKeyStoreParam publicKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseTestSupport.class, publicKeyStore.toString(), ALIAS,
                STORE_PASSWORD, null, SIGNATURE_ALGORITHM);
    }

    /**
     * \u6784\u9020 <code>CustomKeyStoreParam</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>CustomKeyStoreParam</code> \u7ed3\u679c
     */
    CustomKeyStoreParam samePasswordKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseTestSupport.class, samePasswordKeyStore.toString(), ALIAS,
                STORE_PASSWORD, STORE_PASSWORD, SIGNATURE_ALGORITHM);
    }

    /**
     * \u901a\u8fc7 <code>privateKeyStore.toString()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>privateKeyStore.toString()</code>
     */
    String privateKeyStorePath() {
        return privateKeyStore.toString();
    }

    /**
     * \u901a\u8fc7 <code>publicKeyStore.toString()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>publicKeyStore.toString()</code>
     */
    String publicKeyStorePath() {
        return publicKeyStore.toString();
    }
}
