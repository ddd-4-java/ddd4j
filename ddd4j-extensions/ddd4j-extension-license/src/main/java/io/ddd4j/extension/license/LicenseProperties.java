package io.ddd4j.extension.license;

import lombok.Data;

/**
 * License 配置属性类
 *
 * <p>映射配置文件中以 "license" 为前缀的配置项，包括证书主题、公钥别称、证书路径等。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
// @ConfigurationProperties(prefix = LicenseProperties.PREFIX)
@Data
public class LicenseProperties {

    public static final String PREFIX = "license";

    /**
     * 证书subject
     */
    private String subject;

    /**
     * 公钥别称
     */
    private String publicAlias;

    /**
     * 访问公钥库的密码
     */
    private String storePass;

    /**
     * 证书生成路径
     */
    private String licensePath;

    /**
     * 密钥库存储路径
     */
    private String publicKeysStorePath;

}
