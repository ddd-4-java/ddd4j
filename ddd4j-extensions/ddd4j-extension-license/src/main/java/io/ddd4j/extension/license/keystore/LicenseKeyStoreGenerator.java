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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /** \u7b49\u5f85 keytool \u8fdb\u7a0b\u7ed3\u675f\u7684\u6700\u957f\u79d2\u6570\u3002 */
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
     * \u6784\u9020 keytool -genkeypair \u547d\u4ee4\uff08\u751f\u6210\u79c1\u94a5 + \u81ea\u7b7e\u8bc1\u4e66\uff09\u3002
     *
     * @param param \u5bc6\u94a5\u5e93\u751f\u6210\u914d\u7f6e
     * @return \u751f\u6210\u5bc6\u94a5\u5bf9\u7684 keytool \u53c2\u6570\u5217\u8868
     */
    private List<String> keytoolGenkeyCommand(LicenseKeyStoreParam param) {
        List<String> cmd = new ArrayList<>();
        cmd.add(keytoolBinary());
        cmd.add("-genkeypair");
        cmd.add("-alias");
        cmd.add(param.getPrivateAlias());
        cmd.add("-keyalg");
        cmd.add(param.getKeyAlgorithm());
        cmd.add("-keysize");
        cmd.add(String.valueOf(param.getKeySize()));
        cmd.add("-sigalg");
        cmd.add(sigAlgOf(param.getKeyAlgorithm()));
        cmd.add("-validity");
        cmd.add(String.valueOf(param.getValidityDays()));
        cmd.add("-dname");
        cmd.add(param.getDname());
        cmd.add("-storetype");
        cmd.add("JKS");
        cmd.add("-keystore");
        cmd.add(param.getPrivateKeysStorePath());
        cmd.add("-storepass");
        cmd.add(param.getStorePass());
        cmd.add("-keypass");
        cmd.add(param.getKeyPass());
        return cmd;
    }

    /**
     * \u6784\u9020 keytool -exportcert \u547d\u4ee4\uff08\u5bfc\u51fa\u8bc1\u4e66\uff09\u3002
     *
     * @param param \u5bc6\u94a5\u5e93\u751f\u6210\u914d\u7f6e
     * @param certPath \u5bfc\u5165\u6216\u5bfc\u51fa\u7684\u8bc1\u4e66\u6587\u4ef6\u8def\u5f84
     * @return \u5bfc\u51fa\u8bc1\u4e66\u7684 keytool \u53c2\u6570\u5217\u8868
     */
    private List<String> keytoolExportCommand(LicenseKeyStoreParam param, String certPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(keytoolBinary());
        cmd.add("-exportcert");
        cmd.add("-alias");
        cmd.add(param.getPrivateAlias());
        cmd.add("-keystore");
        cmd.add(param.getPrivateKeysStorePath());
        cmd.add("-storepass");
        cmd.add(param.getStorePass());
        cmd.add("-file");
        cmd.add(certPath);
        cmd.add("-rfc");
        return cmd;
    }

    /**
     * \u6784\u9020 keytool -importcert \u547d\u4ee4\uff08\u5bfc\u5165\u8bc1\u4e66\u5230\u516c\u94a5\u5e93\uff09\u3002
     *
     * @param param \u5bc6\u94a5\u5e93\u751f\u6210\u914d\u7f6e
     * @param certPath \u5bfc\u5165\u6216\u5bfc\u51fa\u7684\u8bc1\u4e66\u6587\u4ef6\u8def\u5f84
     * @return \u5bfc\u5165\u8bc1\u4e66\u7684 keytool \u53c2\u6570\u5217\u8868
     */
    private List<String> keytoolImportCommand(LicenseKeyStoreParam param, String certPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(keytoolBinary());
        cmd.add("-importcert");
        cmd.add("-alias");
        cmd.add(param.getPublicAlias());
        cmd.add("-file");
        cmd.add(certPath);
        cmd.add("-keystore");
        cmd.add(param.getPublicKeysStorePath());
        cmd.add("-storepass");
        cmd.add(param.getStorePass());
        cmd.add("-noprompt");
        return cmd;
    }

    /**
     * \u6267\u884c keytool \u547d\u4ee4\uff0c\u5931\u8d25\u65f6\u629b\u51fa\u5f02\u5e38\u5e76\u9644\u5e26 stderr\u3002
     *
     * @param command \u8981\u6267\u884c\u7684\u547d\u4ee4\u548c\u53c2\u6570
     * @throws Exception \u65e0\u6cd5\u542f\u52a8 keytool\u3001\u7b49\u5f85\u4e2d\u65ad\u6216\u547d\u4ee4\u975e\u96f6\u9000\u51fa
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

    /**
     * \u5728\u542f\u52a8 keytool \u524d\u6821\u9a8c\u53e3\u4ee4\u3001\u6761\u76ee\u540d\u79f0\u53ca\u5bc6\u94a5\u5e93\u8def\u5f84\u3002
     * @param param \u751f\u6210\u7b7e\u53d1\u548c\u9a8c\u8bc1\u5bc6\u94a5\u5e93\u7684\u914d\u7f6e
     */
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

    /**
     * \u68c0\u67e5\u8bb8\u53ef\u8bc1\u914d\u7f6e\u4e2d\u7684\u5fc5\u586b\u6587\u672c\u3002
     * @param value \u5f85\u68c0\u67e5\u7684\u914d\u7f6e\u503c
     * @param field \u7528\u4e8e\u8bca\u65ad\u7684\u914d\u7f6e\u5b57\u6bb5\u540d\u79f0
     * @throws IllegalArgumentException \u914d\u7f6e\u672a\u63d0\u4f9b\u6709\u6548\u6587\u672c
     */
    private void requireText(String value, String field) {
        if (Objects.isNull(value) || StrKit.isEmpty(value.trim())) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    /**
     * \u590d\u5236\u5e76\u683c\u5f0f\u5316 keytool \u53c2\u6570\uff0c\u5728\u65e5\u5fd7\u6587\u672c\u4e2d\u9690\u53bb\u5bc6\u94a5\u5e93\u548c\u5bc6\u94a5\u53e3\u4ee4\u3002
     * @param command \u5373\u5c06\u8fd0\u884c\u6216\u8bca\u65ad\u7684 keytool \u53c2\u6570\u5217\u8868
     * @return \u53ef\u4ee5\u5199\u5165\u8bca\u65ad\u7684\u8131\u654f\u547d\u4ee4\u6587\u672c
     */
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
     * \u89e3\u6790 keytool \u53ef\u6267\u884c\u6587\u4ef6\u8def\u5f84\uff08JAVA_HOME/bin/keytool \u6216 PATH \u4e2d\u7684 keytool\uff09\u3002
     *
     * @return \u5f53\u524d Java \u73af\u5883\u53ef\u7528\u7684 keytool \u6267\u884c\u8def\u5f84
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
     * \u6839\u636e\u5bc6\u94a5\u7b97\u6cd5\u9009\u62e9\u7b7e\u540d\u7b97\u6cd5\u3002
     *
     * @param keyAlgorithm \u5bc6\u94a5\u7b97\u6cd5
     * @return \u6307\u5b9a\u5bc6\u94a5\u7b97\u6cd5\u5bf9\u5e94\u7684\u7b7e\u540d\u7b97\u6cd5
     */
    private String sigAlgOf(String keyAlgorithm) {
        if ("DSA".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withDSA";
        }
        return "SHA256withRSA";
    }

    /**
     * \u786e\u4fdd\u76ee\u6807\u6587\u4ef6\u7684\u7236\u76ee\u5f55\u5b58\u5728\u3002
     *
     * @param path \u5f85\u751f\u6210\u7684\u6587\u4ef6\u8def\u5f84
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
