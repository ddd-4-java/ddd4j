package io.ddd4j.boot.cmpt.security.jwt;

import hitool.core.lang3.time.DateUtils;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * JWT 签发配置
 */
@Configuration
@ConfigurationProperties(JwtIssueProperteis.PREFIX)
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
     *  "?ns" //纳秒
     *  "?us" //微秒
     *  "?ms" //毫秒
     *  "?s" //秒
     *  "?m" //分
     *  "?h" //小时
     *  "?d" //天
     */
    @Value("#{T(org.springframework.boot.convert.DurationStyle).detectAndParse('${jwt.expire:7d}')}")
    private Duration expire;
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

}

