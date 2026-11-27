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
import io.ddd4j.core.constant.AuthConstants;
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
     * 签发 JWT 时使用的签名密钥。
     */
    private final String signingSecret;
    /**
     * 写入或校验 JWT 的签发方声明。
     */
    private final String issuer;
    /**
     * 写入或校验 JWT 的受众声明。
     */
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
     * 读取<code>signingSecret</code> 字段。
     *
     * @return <code>signingSecret</code> 字段
     */
    @Override
    public String jwtSecretKey() {
        return signingSecret;
    }

    /**
     * 复制扩展声明并写入配置的签发方与接收方后生成令牌。
     *
     * @param loginId    当前认证域内的账号标识
     * @param deviceType 令牌签发时关联的设备类型
     * @param timeout    原生令牌生成所用的超时时长
     * @param extraData  随令牌一起保存的额外声明
     * @return 带当前账号范围和 JWT 声明的令牌
     */
    @Override
    public String createTokenValue(Object loginId, String deviceType, long timeout, Map<String, Object> extraData) {
        Map<String, Object> payloads = new LinkedHashMap<>();
        if (Objects.nonNull(extraData)) {
            payloads.putAll(extraData);
        }
        payloads.put(AuthConstants.JWT_ISSUER, issuer);
        payloads.put(AuthConstants.JWT_AUDIENCE, audience);
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
            return Objects.equals(issuer, String.valueOf(payloads.get(AuthConstants.JWT_ISSUER)))
                    && Objects.equals(audience, String.valueOf(payloads.get(AuthConstants.JWT_AUDIENCE)));
        } catch (SaJwtException | IllegalArgumentException exception) {
            return false;
        }
    }
}
