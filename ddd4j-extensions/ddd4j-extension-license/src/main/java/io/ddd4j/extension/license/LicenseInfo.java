package io.ddd4j.extension.license;

import de.schlichtherle.license.LicenseContent;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * License 验证后的可对外暴露信息（DTO）。
 *
 * <p>作为 {@code CacheKit} 的缓存值。之所以不直接缓存 TrueLicense 的
 * {@link LicenseContent}，是因为后者依赖 XML 序列化、并非 {@code Serializable}，
 * 当 CacheKit 后端切换到 Redis 等 RPC 缓存时序列化会失败。
 * 本类为纯字段 POJO，本地（Caffeine）与远程（Redis）缓存都安全。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
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
     * 从 TrueLicense 的 {@link LicenseContent} 构造本 DTO。
     *
     * @param content TrueLicense 证书内容
     * @return 对应的 {@link LicenseInfo}；content 为 null 时返回 null
     */
    public static LicenseInfo from(LicenseContent content) {
        if (Objects.isNull(content)) {
            return null;
        }
        Object extra = content.getExtra();
        return LicenseInfo.builder()
                .subject(content.getSubject())
                .issued(content.getIssued())
                .notBefore(content.getNotBefore())
                .notAfter(content.getNotAfter())
                .consumerType(content.getConsumerType())
                .consumerAmount(content.getConsumerAmount())
                .extra(extra instanceof LicenseExtraModel ? (LicenseExtraModel) extra : null)
                .build();
    }

    /**
     * 判断证书当前是否仍在有效期内（notBefore <= now < notAfter）。
     *
     * @return true 表示仍在有效期内
     */
    public boolean isValidNow() {
        Date now = new Date();
        if (Objects.nonNull(notBefore) && now.before(notBefore)) {
            return false;
        }
        return Objects.isNull(notAfter) || !now.after(notAfter);
    }
}
