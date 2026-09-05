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
package io.ddd4j.extension.license.keystore;

import io.ddd4j.kit.lang.StrKit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * License 公私钥库生成器。
 *
 * <p>通过调用随 JDK 自带的 {@code keytool} 命令生成一对密钥库：
 * <ul>
 *   <li><b>私钥库</b>（privateKeys.keystore）：发证方 {@code LicenseCreator} 持有，用于签名</li>
 *   <li><b>公钥库</b>（publicCerts.keystore）：校验方 {@code LicenseVerify} 持有，用于验签</li>
 * </ul>
 *
 * <p>采用 {@code keytool} 而非手写 JCA 自签证书，原因：
 * <ul>
 *   <li>{@code keytool} 随所有 JDK 发行版自带，零额外依赖；</li>
 *   <li>JDK 16+ 起 {@code sun.security.x509.*} 被强封装，手写自签需 {@code --add-exports}，污染编译参数。</li>
 * </ul>
 *
 * <p>用途：让端到端流程无需手动执行 keytool，测试与初始化脚本可直接调用本类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class LicenseKeyStoreGenerator {

    private static final long KEYTOOL_TIMEOUT_SECONDS = 60L;

    /**
     * 生成公私钥库对并落盘。
     *
     * <p>内部流程：
     * <ol>
     *   <li>用 {@code keytool -genkeypair} 在私钥库中生成私钥 + 自签证书；</li>
     *   <li>用 {@code keytool -exportcert} 从私钥库导出证书为 DER 文件；</li>
     *   <li>用 {@code keytool -importcert} 把证书导入公钥库（作为可信证书）。</li>
     * </ol>
     *
     * @param param 生成参数
     * @return 落盘结果（含两个文件的绝对路径）
     * @throws Exception 生成失败时抛出
     */
    public GenerateResult generate(LicenseKeyStoreParam param) throws Exception {
        validateParam(param);

        ensureParentDir(param.getPrivateKeysStorePath());
        ensureParentDir(param.getPublicKeysStorePath());

        // 临时证书文件（导入公钥库后删除）
        Path certFile = Paths.get(param.getPublicKeysStorePath() + ".cer");

        try {
            // 1. 生成私钥 + 自签证书到私钥库
            run(keytoolGenkeyCommand(param));

            // 2. 从私钥库导出证书
            run(keytoolExportCommand(param, certFile.toString()));

            // 3. 把证书导入公钥库（公钥库可作为校验方信任库）
            run(keytoolImportCommand(param, certFile.toString()));

            log.info("License 密钥库生成成功: private={}, public={}",
                    param.getPrivateKeysStorePath(), param.getPublicKeysStorePath());
            return new GenerateResult(param.getPrivateKeysStorePath(), param.getPublicKeysStorePath());
        } finally {
            try {
                Files.deleteIfExists(certFile);
            } catch (Exception ignored) {
                // 临时证书清理失败不影响主流程
            }
        }
    }

    /**
     * 构造 keytool -genkeypair 命令（生成私钥 + 自签证书）。
     */
    private List<String> keytoolGenkeyCommand(LicenseKeyStoreParam param) {
        List<String> cmd = new ArrayList<>();
        cmd.add(keytoolBinary());
        cmd.add("-genkeypair");
        cmd.add("-alias"); cmd.add(param.getPrivateAlias());
        cmd.add("-keyalg"); cmd.add(param.getKeyAlgorithm());
        cmd.add("-keysize"); cmd.add(String.valueOf(param.getKeySize()));
        cmd.add("-sigalg"); cmd.add(sigAlgOf(param.getKeyAlgorithm()));
        cmd.add("-validity"); cmd.add(String.valueOf(param.getValidityDays()));
        cmd.add("-dname"); cmd.add(param.getDname());
        cmd.add("-storetype"); cmd.add("JKS");
        cmd.add("-keystore"); cmd.add(param.getPrivateKeysStorePath());
        cmd.add("-storepass"); cmd.add(param.getStorePass());
        cmd.add("-keypass"); cmd.add(param.getKeyPass());
        return cmd;
    }

    /**
     * 构造 keytool -exportcert 命令（导出证书）。
     */
    private List<String> keytoolExportCommand(LicenseKeyStoreParam param, String certPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(keytoolBinary());
        cmd.add("-exportcert");
        cmd.add("-alias"); cmd.add(param.getPrivateAlias());
        cmd.add("-keystore"); cmd.add(param.getPrivateKeysStorePath());
        cmd.add("-storepass"); cmd.add(param.getStorePass());
        cmd.add("-file"); cmd.add(certPath);
        cmd.add("-rfc");
        return cmd;
    }

    /**
     * 构造 keytool -importcert 命令（导入证书到公钥库）。
     */
    private List<String> keytoolImportCommand(LicenseKeyStoreParam param, String certPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(keytoolBinary());
        cmd.add("-importcert");
        cmd.add("-alias"); cmd.add(param.getPublicAlias());
        cmd.add("-file"); cmd.add(certPath);
        cmd.add("-keystore"); cmd.add(param.getPublicKeysStorePath());
        cmd.add("-storepass"); cmd.add(param.getStorePass());
        cmd.add("-noprompt");
        return cmd;
    }

    /**
     * 执行 keytool 命令，失败时抛出异常并附带 stderr。
     */
    private void run(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        if (!process.waitFor(KEYTOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("keytool 执行超时: " + redactedCommand(command));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.exitValue();
        if (code != 0) {
            throw new IllegalStateException("keytool 执行失败 (code=" + code + "): " + output
                    + "\n命令: " + redactedCommand(command));
        }
    }

    private void validateParam(LicenseKeyStoreParam param) {
        Objects.requireNonNull(param, "LicenseKeyStoreParam 不能为空");
        requireText(param.getPrivateAlias(), "privateAlias");
        requireText(param.getPublicAlias(), "publicAlias");
        requireText(param.getStorePass(), "storePass");
        requireText(param.getKeyPass(), "keyPass");
        requireText(param.getPrivateKeysStorePath(), "privateKeysStorePath");
        requireText(param.getPublicKeysStorePath(), "publicKeysStorePath");
        if (param.getStorePass().length() < 6 || param.getKeyPass().length() < 6) {
            throw new IllegalArgumentException("JKS 密码长度不能少于 6 位");
        }
        Path privatePath = Paths.get(param.getPrivateKeysStorePath()).toAbsolutePath().normalize();
        Path publicPath = Paths.get(param.getPublicKeysStorePath()).toAbsolutePath().normalize();
        if (Objects.equals(privatePath, publicPath)) {
            throw new IllegalArgumentException("公钥库与私钥库不能使用同一路径");
        }
        if (Files.exists(privatePath) || Files.exists(publicPath)) {
            throw new IllegalStateException("目标密钥库已存在，禁止覆盖: "
                    + (Files.exists(privatePath) ? privatePath : publicPath));
        }
        if (param.getValidityDays() <= 0 || param.getKeySize() <= 0) {
            throw new IllegalArgumentException("validityDays 与 keySize 必须大于 0");
        }
    }

    private void requireText(String value, String field) {
        if (Objects.isNull(value) || StrKit.isEmpty(value.trim())) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    private String redactedCommand(List<String> command) {
        List<String> redacted = new ArrayList<>(command);
        for (int index = 0; index < redacted.size() - 1; index++) {
            String argument = redacted.get(index);
            if (Objects.equals("-storepass", argument) || Objects.equals("-keypass", argument)) {
                redacted.set(index + 1, "******");
            }
        }
        return String.join(" ", redacted);
    }

    /**
     * 解析 keytool 可执行文件路径（JAVA_HOME/bin/keytool 或 PATH 中的 keytool）。
     */
    private String keytoolBinary() {
        String javaHome = System.getProperty("java.home");
        Path bin = Paths.get(javaHome, "bin", "keytool");
        if (Files.exists(bin)) {
            return bin.toString();
        }
        return "keytool";
    }

    /**
     * 根据密钥算法选择签名算法。
     */
    private String sigAlgOf(String keyAlgorithm) {
        if ("DSA".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withDSA";
        }
        return "SHA256withRSA";
    }

    /**
     * 确保目标文件的父目录存在。
     */
    private void ensureParentDir(String path) {
        File f = new File(path);
        File parent = f.getParentFile();
        if (Objects.nonNull(parent) && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建密钥库目录: " + parent);
        }
    }

    /**
     * 生成结果。
     */
    @Data
    @AllArgsConstructor
    public static class GenerateResult {
        /**
         * 私钥库文件路径
         */
        private String privateKeysStorePath;
        /**
         * 公钥库文件路径
         */
        private String publicKeysStorePath;
    }
}
