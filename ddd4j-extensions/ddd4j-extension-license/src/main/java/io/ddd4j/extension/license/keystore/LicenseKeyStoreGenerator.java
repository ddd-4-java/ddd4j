package io.ddd4j.extension.license.keystore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        Objects.requireNonNull(param.getPrivateKeysStorePath(), "privateKeysStorePath 不能为空");
        Objects.requireNonNull(param.getPublicKeysStorePath(), "publicKeysStorePath 不能为空");

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
        String output = new String(process.getInputStream().readAllBytes());
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("keytool 执行失败 (code=" + code + "): " + output
                    + "\n命令: " + String.join(" ", command));
        }
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
