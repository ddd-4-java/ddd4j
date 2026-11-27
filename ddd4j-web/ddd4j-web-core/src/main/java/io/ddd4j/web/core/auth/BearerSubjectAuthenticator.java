package io.ddd4j.web.core.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

import java.util.Objects;
import java.util.Optional;

import io.ddd4j.web.core.error.WebStatusException;

/**
 * 将标准 Bearer Token 委托给当前运行时注册的 Subject SPI。
 */
public final class BearerSubjectAuthenticator {

    /** \u4ece HTTP Authorization \u5934\u8bfb\u53d6 Bearer \u4ee4\u724c\u7684\u7ec4\u4ef6\u3002 */
    private final BearerTokenResolver tokenResolver;

    /** \u4f7f\u7528\u9ed8\u8ba4 {@link BearerTokenResolver} \u6784\u9020\u8ba4\u8bc1\u5668\u3002 */
    public BearerSubjectAuthenticator() {
        this(new BearerTokenResolver());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u7684 Bearer Token \u89e3\u6790\u5668\u6784\u9020\u8ba4\u8bc1\u5668\u3002
     * @param tokenResolver Bearer Token \u89e3\u6790\u5668\uff0c\u4e0d\u53ef\u4e3a {@code null}
     */
    public BearerSubjectAuthenticator(BearerTokenResolver tokenResolver) {
        this.tokenResolver = Objects.requireNonNull(tokenResolver, "tokenResolver must not be null");
    }

    /**
     * \u8ba4\u8bc1 {@code Authorization} \u5934\u5e76\u8fd4\u56de\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param authorization \u539f\u59cb Authorization \u5934\u503c
     * @return \u8ba4\u8bc1\u901a\u8fc7\u7684\u4e3b\u4f53
     * @throws WebStatusException \u7f3a\u5c11 Bearer Token\u3001Subject SPI \u4e0d\u53ef\u7528\u6216\u4ee4\u724c\u65e0\u6548\u65f6\u629b\u51fa
     */
    public AuthPrincipal authenticate(String authorization) {
        return authenticateSubject(authorization).principal();
    }

    /**
     * \u8ba4\u8bc1 {@code Authorization} \u5934\u5e76\u8fd4\u56de\u5b8c\u6574\u8ba4\u8bc1\u7ed3\u679c\u3002
     * @param authorization \u539f\u59cb Authorization \u5934\u503c
     * @return \u5305\u542b\u4ee4\u724c\u3001\u4e3b\u4f53\u4e0e Subject SPI \u7684\u8ba4\u8bc1\u7ed3\u679c
     * @throws WebStatusException \u7f3a\u5c11 Bearer Token\u3001Subject SPI \u4e0d\u53ef\u7528\u6216\u4ee4\u724c\u65e0\u6548\u65f6\u629b\u51fa
     */
    public Authentication authenticateSubject(String authorization) {
        String token = tokenResolver.resolve(authorization)
                .orElseThrow(() -> new WebStatusException(401, "Bearer token is required"));
        return authenticateToken(token);
    }

    /**
     * \u89e3\u6790 Authorization \u5934\uff0c\u5e76\u9a8c\u8bc1\u89e3\u6790\u51fa\u7684 Bearer Token\u3002
     * @param authorization \u539f\u59cb Authorization \u5934\u503c
     * @return \u8ba4\u8bc1\u7ed3\u679c\u3002\u5982\u679c\u672a\u89e3\u6790\u51fa Bearer Token\uff0c\u8fd4\u56de {@link Optional#empty()}
     * @throws WebStatusException \u5df2\u89e3\u6790\u51fa\u4ee4\u724c\u4e14 Subject SPI \u4e0d\u53ef\u7528\uff0c\u6216\u4ee4\u724c\u65e0\u6548\u6216\u8fc7\u671f\u65f6\u629b\u51fa
     */
    public Optional<Authentication> authenticateOptional(String authorization) {
        return tokenResolver.resolve(authorization).map(this::authenticateToken);
    }

    /**
     * \u6821\u9a8c Bearer \u4ee4\u724c\u5e76\u89e3\u6790\u5f53\u524d\u8bf7\u6c42\u4e3b\u4f53\u3002
     * @param token \u4ece Authorization \u8bfb\u53d6\u7684 Bearer \u8bbf\u95ee\u4ee4\u724c
     * @return \u4ee4\u724c\u6709\u6548\u65f6\u7684\u8ba4\u8bc1\u7ed3\u679c
     */
    private Authentication authenticateToken(String token) {
        SubjectProvider provider = Contexts.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class)
                .orElseThrow(() -> new WebStatusException(401, "Subject provider is unavailable"));
        Subject subject = provider.getSubject();
        AuthPrincipal principal = subject.verify(token);
        if (Objects.isNull(principal)) {
            throw new WebStatusException(401, "Bearer token is invalid or expired");
        }
        return new Authentication(token, principal, subject);
    }

    /** \u8ba4\u8bc1\u7ed3\u679c\uff1a\u4ee4\u724c\u3001\u8ba4\u8bc1\u4e3b\u4f53\u4e0e\u7b7e\u53d1\u8be5\u4e3b\u4f53\u7684 Subject SPI\u3002 */
    public final static class Authentication {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u5df2\u4ece\u5f53\u524d\u8bf7\u6c42\u8bfb\u51fa\u5e76\u6821\u9a8c\u7684\u8ba4\u8bc1\u4ee4\u724c\u3002 */
        private final String token;

        /** \u5f53\u524d\u8bf7\u6c42\u8ba4\u8bc1\u540e\u7684\u4e1a\u52a1\u4e3b\u4f53\u4fe1\u606f\u3002 */
        private final AuthPrincipal principal;

        /** \u5f53\u524d\u8bf7\u6c42\u6240\u7ed1\u5b9a\u7684\u4e3b\u4f53\u64cd\u4f5c\u5165\u53e3\u3002 */
        private final Subject subject;

        /**
         * \u6784\u9020\u8ba4\u8bc1\u7ed3\u679c\u3002
         * @param token \u901a\u8fc7\u6821\u9a8c\u7684 Bearer Token
         * @param principal \u8ba4\u8bc1\u901a\u8fc7\u7684\u4e3b\u4f53
         * @param subject \u5b8c\u6210\u6821\u9a8c\u7684 Subject SPI
         */
        @JsonCreator()
        public Authentication(@JsonProperty("token") String token, @JsonProperty("principal") AuthPrincipal principal, @JsonProperty("subject") Subject subject) {
            this.token = token;
            this.principal = principal;
            this.subject = subject;
        }

        /**
         * \u8fd4\u56de\u8eab\u4efd\u4ee4\u724c\u3002
         * @return \u8eab\u4efd\u4ee4\u724c
         */
        @JsonProperty("token")
        public String token() {
            return token;
        }

        /**
         * \u8fd4\u56de\u8ba4\u8bc1\u4e3b\u4f53\u3002
         * @return \u8ba4\u8bc1\u4e3b\u4f53
         */
        @JsonProperty("principal")
        public AuthPrincipal principal() {
            return principal;
        }

        /**
         * \u8fd4\u56deSubject \u5b9e\u4f8b\u3002
         * @return Subject \u5b9e\u4f8b
         */
        @JsonProperty("subject")
        public Subject subject() {
            return subject;
        }

        /**
         * 根据<code>token</code>、<code>principal</code>、<code>subject</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            Authentication other = (Authentication) obj;
            return Objects.equals(this.token, other.token) && Objects.equals(this.principal, other.principal) && Objects.equals(this.subject, other.subject);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(token);
            result = 31 * result + Objects.hashCode(principal);
            result = 31 * result + Objects.hashCode(subject);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>token</code>、<code>principal</code>、<code>subject</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "Authentication[token=" + token + ", principal=" + principal + ", subject=" + subject + "]";
        }
    }
}
