package io.ddd4j.sample.dropwizard.shiro;


import com.google.common.collect.ImmutableSet;
import io.ddd4j.web.core.error.WebStatusException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import java.util.Objects;
import java.util.Set;

/**
 * 单应用原生 Shiro Realm 和会话管理；固定凭证仅用于样例。
 */

public class NativeAuth implements AutoCloseable {
    /** \u5f53\u524d\u793a\u4f8b\u5b89\u88c5\u5e76\u8d1f\u8d23\u5173\u95ed\u7684 Shiro \u5b89\u5168\u7ba1\u7406\u5668\u3002 */
    private final DefaultSecurityManager owned = new DefaultSecurityManager(new SampleRealm());

    /**
     * @return 本样例拥有的引擎
     */
    public SecurityManager manager() {
        return owned;
    }

    /**
     * @param username 账号
     * @param password 凭证
     * @return 原生会话标识
     */
    public String login(String username, String password) {
        if (Objects.isNull(username)) {
            throw new WebStatusException(401, "invalid credentials");
        }
        try {
            return new io.ddd4j.auth.shiro.subject.ShiroSubjectProvider().getSubject().login(
                    io.ddd4j.core.auth.AuthRequest.of(username)
                            .setPrincipal(new io.ddd4j.core.auth.AuthPrincipal().setLoginId(username).setUserId(username))
                            .extra("credential", password));
        } catch (io.ddd4j.core.exception.AuthenticationException failure) {
            throw new WebStatusException(401, "invalid credentials");
        }
    }

    /**
     * 销毁本实例创建的引擎和会话资源。
     */
    @Override
    public void close() {
        owned.destroy();
    }

    /** \u6839\u636e\u793a\u4f8b\u7528\u6237\u53ca\u8ba2\u5355\u6743\u9650\u6267\u884c\u539f\u751f Shiro \u8ba4\u8bc1\u4e0e\u6388\u6743\u3002 */
    private static final class SampleRealm extends AuthorizingRealm {
        /** {@inheritDoc} */
        @Override
        protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
            String user = String.valueOf(token.getPrincipal());
            if (!ImmutableSet.of("alice", "reader").contains(user)) {
                throw new UnknownAccountException("unknown sample account");
            }
            return new SimpleAuthenticationInfo(user, "sample-password", getName());
        }

        /** {@inheritDoc} */
        @Override
        protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
            SimpleAuthorizationInfo result = new SimpleAuthorizationInfo();
            result.setStringPermissions("alice".equals(principals.getPrimaryPrincipal()) ? ImmutableSet.of("order:read", "order:write") : ImmutableSet.of("order:read"));
            return result;
        }
    }
}
