package io.ddd4j.extension.license.creator;

import io.ddd4j.extension.license.LicenseExtraModel;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * License生成类需要的参数
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class LicenseCreatorParam implements Serializable {

    /**
     * 证书subject（与校验时的subject对应）
     */
    private String subject;

    /**
     * 私钥别称
     */
    private String privateAlias;

    /**
     * 私钥密码（需要妥善保管，不能让使用者知道）
     */
    private String keyPass;

    /**
     * 访问私钥库的密码
     */
    private String storePass;

    /**
     * 证书生成路径
     */
    private String licensePath;

    /**
     * 私钥库存储路径
     */
    private String privateKeysStorePath;

    /**
     * 证书生效时间
     */
    private Date issuedTime = new Date();

    /**
     * 证书失效时间
     */
    private Date expiryTime;

    /**
     * 用户类型
     */
    private String consumerType = "user";

    /**
     * 用户数量
     */
    private Integer consumerAmount = 1;

    /**
     * 描述信息
     */
    private String description = "";

    /**
     * 额外的服务器硬件校验信息
     */
    private LicenseExtraModel licenseExtraModel;

}
