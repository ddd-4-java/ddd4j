package io.ddd4j.auth.satoken.config;

import cn.dev33.satoken.jwt.SaJwtUtil;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.jwt.exception.SaJwtException;
import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 带 issuer/audience 约束的 Sa-Token JWT Simple Logic。
 *
 * <p>它仍使用 JWT Simple 的服务端 token 映射；因此登出、踢人和过期均可立即失效，
 * 不把无状态 JWT 冒充为可撤销会话。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
public class Ddd4jStpLogicJwtForSimple extends StpLogicJwtForSimple {

    public static final String ISSUER_CLAIM = "iss";
    public static final String AUDIENCE_CLAIM = "aud";

    private final String issuer;
    private final String audience;

    public Ddd4jStpLogicJwtForSimple(SaTokenSecurityProperties properties) {
        super(properties.getLoginType());
        this.issuer = properties.getJwtIssuer();
        this.audience = properties.getJwtAudience();
    }

    @Override
    public String createTokenValue(Object loginId, String deviceType, long timeout, Map<String, Object> extraData) {
        Map<String, Object> payloads = new LinkedHashMap<>();
        if (Objects.nonNull(extraData)) {
            payloads.putAll(extraData);
        }
        payloads.put(ISSUER_CLAIM, issuer);
        payloads.put(AUDIENCE_CLAIM, audience);
        return SaJwtUtil.createToken(getLoginType(), loginId, deviceType, timeout, payloads, jwtSecretKey());
    }

    /**
     * 校验签名、账号体系和 ddd4j 附加的 issuer/audience 声明。
     */
    public boolean hasExpectedClaims(String token) {
        if (StrKit.isBlank(token)) {
            return false;
        }
        try {
            Map<String, Object> payloads = SaJwtUtil.getPayloadsNotCheck(token, getLoginType(), jwtSecretKey());
            return Objects.equals(issuer, String.valueOf(payloads.get(ISSUER_CLAIM)))
                    && Objects.equals(audience, String.valueOf(payloads.get(AUDIENCE_CLAIM)));
        } catch (SaJwtException | IllegalArgumentException exception) {
            return false;
        }
    }
}
