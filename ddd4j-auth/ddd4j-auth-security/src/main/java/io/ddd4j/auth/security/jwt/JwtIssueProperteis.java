package io.ddd4j.auth.security.jwt;

import hitool.core.lang3.time.DateUtils;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * JWT 签发配置
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
@Data
public class JwtIssueProperteis {

    public static final String PREFIX = "jwt";
    // 过期时间（7天），单位毫秒
    public static final long EXPIRE_TIME = 7 * DateUtils.MILLIS_PER_DAY;

    /**
     * JWT 签发者
     */
    @Value("${jwt.issuer:www.example.com}")
    private String issuer;

    @Value("${jwt.seed:123456}")
    private String seed;

    @Value("${jwt.aec-key:123456}")
    private String aecKey;

    @Value("${jwt.aec-iv:123456}")
    private String aecIv;
    /**
     * 过期时间（7天），1d
     * "?ns" //纳秒
     * "?us" //微秒
     * "?ms" //毫秒
     * "?s" //秒
     * "?m" //分
     * "?h" //小时
     * "?d" //天
     */
    private Duration expire = Duration.ofDays(7);
    /**
     * JWT 加密算法
     */
    @Value("${jwt.algorithm:HS256}")
    private String algorithm;

    @Value("${jwt.hmac-key:123456}")
    private String hmacKey;

    @Value("${jwt.rsa-pri-key:}")
    private String rsaPriKey;

    @Value("${jwt.rsa-pub-key:}")
    private String rsaPubKey;

    @Value("${jwt.expire:7d}")
    public void setExpire(String expire) {
        this.expire = parseDuration(expire);
    }

    private Duration parseDuration(String value) {
        if (!StringUtils.hasText(value)) {
            return Duration.ofMillis(EXPIRE_TIME);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("ns")) {
            return Duration.ofNanos(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("us")) {
            return Duration.ofNanos(Long.parseLong(normalized.substring(0, normalized.length() - 2)) * 1_000L);
        }
        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.parse(value);
    }
}
