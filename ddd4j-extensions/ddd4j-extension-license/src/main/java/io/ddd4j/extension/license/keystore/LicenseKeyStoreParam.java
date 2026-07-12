package io.ddd4j.extension.license.keystore;

import lombok.Builder;
import lombok.Data;

/**
 * 密钥库生成参数。
 *
 * <p>由 {@link LicenseKeyStoreGenerator} 消费，用于编程式生成一对公私钥库
 * （分别供发证方 / 校验方使用），无需依赖 {@code keytool} 命令。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
public class LicenseKeyStoreParam {

    /**
     * 私钥库条目别名（默认 privateKey）
     */
    @Builder.Default
    private String privateAlias = "privateKey";
    /**
     * 公钥库条目别名（默认 publicCert）
     */
    @Builder.Default
    private String publicAlias = "publicCert";
    /**
     * 私钥库访问密码
     */
    @Builder.Default
    private String storePass = "a123456";
    /**
     * 私钥条目密码
     */
    @Builder.Default
    private String keyPass = "a123456";
    /**
     * 密钥算法（默认 RSA）
     */
    @Builder.Default
    private String keyAlgorithm = "RSA";
    /**
     * 密钥长度（默认 2048）
     */
    @Builder.Default
    private int keySize = 2048;
    /**
     * 证书颁发者 / 持有者 X.500 标识名
     */
    @Builder.Default
    private String dname = "CN=localhost, OU=localhost, O=localhost, L=SH, ST=SH, C=CN";
    /**
     * 证书有效期（天，默认 3650 ≈ 10 年）
     */
    @Builder.Default
    private int validityDays = 3650;
    /**
     * 私钥库输出路径（完整文件路径）
     */
    private String privateKeysStorePath;
    /**
     * 公钥库输出路径（完整文件路径）
     */
    private String publicKeysStorePath;
}
