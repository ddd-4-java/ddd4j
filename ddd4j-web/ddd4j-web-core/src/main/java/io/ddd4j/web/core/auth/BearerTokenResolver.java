package io.ddd4j.web.core.auth;

import io.ddd4j.kit.lang.StrKit;

import java.util.Optional;

/**
 * 只接受 RFC 6750 标准 Authorization Bearer 头。
 */
public final class BearerTokenResolver {

    /** Authorization \u4e2d\u4f7f\u7528\u7684 Bearer \u534f\u8bae\u524d\u7f00\u3002 */
    private static final String PREFIX = "Bearer ";

    /**
     * \u4ece\u6807\u51c6 Authorization Bearer \u8bf7\u6c42\u5934\u89e3\u6790\u4ee4\u724c\u3002
     * @param authorization Authorization \u8bf7\u6c42\u5934
     * @return \u975e\u7a7a Bearer \u4ee4\u724c\uff1b\u683c\u5f0f\u4e0d\u5408\u6cd5\u65f6\u4e3a\u7a7a
     */
    public Optional<String> resolve(String authorization) {
        if (StrKit.isBlank(authorization) || !authorization.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }
        String token = authorization.substring(PREFIX.length()).trim();
        return StrKit.isBlank(token) ? Optional.empty() : Optional.of(token);
    }
}
