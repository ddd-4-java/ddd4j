package io.ddd4j.extension.license;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * License 验证后的可对外暴露信息（DTO）。
 *
 * <p>作为 {@code CacheKit} 的缓存值和签名许可证的 JSON 负载。本类为纯字段 POJO，
 * 本地（Caffeine）与远程（Redis）缓存均可安全序列化。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 证书 subject（与校验端一致）
     */
    private String subject;
    /**
     * 颁发时间
     */
    private Date issued;
    /**
     * 生效时间
     */
    private Date notBefore;
    /**
     * 失效时间
     */
    private Date notAfter;
    /**
     * 用户类型
     */
    private String consumerType;
    /**
     * 用户数量
     */
    private Integer consumerAmount;
    /**
     * 附加扩展校验信息（IP/MAC/SN 等），可能为 null
     */
    private LicenseExtraModel extra;

    /**
     * 判断证书当前是否仍在有效期内（notBefore <= now < notAfter）。
     *
     * @return true 表示仍在有效期内
     */
    public boolean isValidNow() {
        return isValidAt(new Date());
    }

    /**
     * 判断证书在指定时间点是否有效，便于业务时钟控制与测试。
     *
     * @param instant 待判断时间
     * @return true 表示证书有效
     */
    public boolean isValidAt(Date instant) {
        Objects.requireNonNull(instant, "instant 不能为空");
        Date now = new Date(instant.getTime());
        if (Objects.nonNull(notBefore) && now.before(notBefore)) {
            return false;
        }
        return Objects.isNull(notAfter) || now.before(notAfter);
    }
}
