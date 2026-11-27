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

    /**
     * 测试密钥库内用于查找证书或密钥的别名。
     */
    static final String ALIAS = "ddd4j-test";
    /**
     * 构造本组测试输入时复用的固定标识或名称。
     */
    static final String KEY_PASSWORD = "ddd4j-test-key-password";
    /**
     * 测试签发和核验许可证所使用的算法。
     */
    static final String SIGNATURE_ALGORITHM = "SHA256withDSA";
    /**
     * 创建和读取测试密钥库时使用的口令。
     */
    static final String STORE_PASSWORD = "ddd4j-test-store-password";

    /**
     * 用于生成并保存测试签发密钥的密钥库。
     */
    private final Path privateKeyStore;
    /**
     * 用于读取测试验签公钥的密钥库。
     */
    private final Path publicKeyStore;
    /**
     * 用同一测试口令创建的密钥库夹具。
     */
    private final Path samePasswordKeyStore;

    /**
     * 构造供当前测试使用的 <code>LicenseTestSupport</code> 实例。
     *
     * @param directory 用于初始化 <code>LicenseTestSupport</code> 的 <code>Path</code> 值
     * @throws Exception 准备或读取夹具文件时失败
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
     * 调用 <code>System.getProperty()</code>、<code>command.add()</code>、<code>executable.getAbsolutePath()</code> 执行当前夹具操作。
     *
     * @param arguments 传给 <code>command.addAll()</code> 的 <code>String[]</code> 输入
     * @throws Exception 准备或读取夹具文件时失败
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
     * 创建 <code>new String</code> 供当前测试路径调用。
     *
     * @param inputStream 传给 <code>inputStream.read()</code> 的 <code>InputStream</code> 输入
     * @return 当前测试使用的结果，取自 <code>new String(outputStream.toByteArray(), StandardCharsets.UTF_8)</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 构造 <code>CustomKeyStoreParam</code> 作为当前测试的输入或结果。
     *
     * @return 提供给后续契约断言的 <code>CustomKeyStoreParam</code> 结果
     */
    CustomKeyStoreParam privateKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseTestSupport.class, privateKeyStore.toString(), ALIAS,
                STORE_PASSWORD, KEY_PASSWORD, SIGNATURE_ALGORITHM);
    }

    /**
     * 创建 <code>new CustomKeyStoreParam</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new CustomKeyStoreParam(LicenseTestSupport.class, publicKeyStore.toString(), ALIAS, STORE_PASSWORD, null, SIGNATURE_ALGORITHM)</code>
     */
    CustomKeyStoreParam publicKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseTestSupport.class, publicKeyStore.toString(), ALIAS,
                STORE_PASSWORD, null, SIGNATURE_ALGORITHM);
    }

    /**
     * 构造 <code>CustomKeyStoreParam</code> 作为当前测试的输入或结果。
     *
     * @return 提供给后续契约断言的 <code>CustomKeyStoreParam</code> 结果
     */
    CustomKeyStoreParam samePasswordKeyStoreParam() {
        return new CustomKeyStoreParam(LicenseTestSupport.class, samePasswordKeyStore.toString(), ALIAS,
                STORE_PASSWORD, STORE_PASSWORD, SIGNATURE_ALGORITHM);
    }

    /**
     * 通过 <code>privateKeyStore.toString()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>privateKeyStore.toString()</code>
     */
    String privateKeyStorePath() {
        return privateKeyStore.toString();
    }

    /**
     * 通过 <code>publicKeyStore.toString()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>publicKeyStore.toString()</code>
     */
    String publicKeyStorePath() {
        return publicKeyStore.toString();
    }
}
