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

    /**
     * JWT 签发方声明名称。
     */
    public static final String ISSUER_CLAIM = "iss";
    /**
     * JWT 受众声明名称。
     */
    public static final String AUDIENCE_CLAIM = "aud";

    /** \u7b7e\u53d1 JWT \u65f6\u4f7f\u7528\u7684\u7b7e\u540d\u5bc6\u94a5\u3002 */
    private final String signingSecret;
    /** \u5199\u5165\u6216\u6821\u9a8c JWT \u7684\u7b7e\u53d1\u65b9\u58f0\u660e\u3002 */
    private final String issuer;
    /** \u5199\u5165\u6216\u6821\u9a8c JWT \u7684\u53d7\u4f17\u58f0\u660e\u3002 */
    private final String audience;

    /**
     * 使用安全配置创建 JWT Simple 账号逻辑。
     *
     * @param properties Sa-Token 安全配置
     */
    public Ddd4jStpLogicJwtForSimple(SaTokenSecurityProperties properties) {
        super(properties.getLoginType());
        this.signingSecret = properties.getJwtSecretKey();
        this.issuer = properties.getJwtIssuer();
        this.audience = properties.getJwtAudience();
    }

    /**
     * \u8bfb\u53d6<code>signingSecret</code> \u5b57\u6bb5\u3002
     * @return <code>signingSecret</code> \u5b57\u6bb5
     */
    @Override
    public String jwtSecretKey() {
        return signingSecret;
    }

    /**
     * \u590d\u5236\u6269\u5c55\u58f0\u660e\u5e76\u5199\u5165\u914d\u7f6e\u7684\u7b7e\u53d1\u65b9\u4e0e\u63a5\u6536\u65b9\u540e\u751f\u6210\u4ee4\u724c\u3002
     * @param loginId \u5f53\u524d\u8ba4\u8bc1\u57df\u5185\u7684\u8d26\u53f7\u6807\u8bc6
     * @param deviceType \u4ee4\u724c\u7b7e\u53d1\u65f6\u5173\u8054\u7684\u8bbe\u5907\u7c7b\u578b
     * @param timeout \u539f\u751f\u4ee4\u724c\u751f\u6210\u6240\u7528\u7684\u8d85\u65f6\u65f6\u957f
     * @param extraData \u968f\u4ee4\u724c\u4e00\u8d77\u4fdd\u5b58\u7684\u989d\u5916\u58f0\u660e
     * @return \u5e26\u5f53\u524d\u8d26\u53f7\u8303\u56f4\u548c JWT \u58f0\u660e\u7684\u4ee4\u724c
     */
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
     *
     * @param token 待校验的 Token
     * @return 声明与当前配置一致时为 true
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
