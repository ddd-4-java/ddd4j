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

    static final String ALIAS = "ddd4j-test";
    static final String KEY_PASSWORD = "ddd4j-test-key-password";
    static final String SIGNATURE_ALGORITHM = "SHA256withDSA";
    static final String STORE_PASSWORD = "ddd4j-test-store-password";

    private final Path privateKeyStore;
    private final Path publicKeyStore;
    private final Path samePasswordKeyStore;

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

    CustomKeyStoreParam privateKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseTestSupport.class, privateKeyStore.toString(), ALIAS,
                STORE_PASSWORD, KEY_PASSWORD, SIGNATURE_ALGORITHM);
    }

    CustomKeyStoreParam publicKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseTestSupport.class, publicKeyStore.toString(), ALIAS,
                STORE_PASSWORD, null, SIGNATURE_ALGORITHM);
    }

    CustomKeyStoreParam samePasswordKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseTestSupport.class, samePasswordKeyStore.toString(), ALIAS,
                STORE_PASSWORD, STORE_PASSWORD, SIGNATURE_ALGORITHM);
    }

    String privateKeyStorePath() {
        return privateKeyStore.toString();
    }

    String publicKeyStorePath() {
        return publicKeyStore.toString();
    }

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

    private static String read(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = inputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, count);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }
}
