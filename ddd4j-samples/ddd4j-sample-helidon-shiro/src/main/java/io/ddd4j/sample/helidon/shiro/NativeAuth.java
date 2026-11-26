package io.ddd4j.sample.helidon.shiro;

import cn.hutool.core.util.ReflectUtil;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.shiro.SecurityUtils;
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
@ApplicationScoped
public class NativeAuth {
    private final SecurityManager previous = current();
    private final DefaultSecurityManager owned = new DefaultSecurityManager(new SampleRealm());

    /**
     * 安装当前样例的原生引擎。
     */
    public NativeAuth() {
        if (Objects.isNull(previous)) {
            SecurityUtils.setSecurityManager(owned);
        }
    }

    private static SecurityManager current() {
        return (SecurityManager) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SecurityUtils.class, "securityManager")));
    }

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
        try {
            return new io.ddd4j.auth.shiro.subject.ShiroSubjectProvider().getSubject().login(
                    io.ddd4j.core.auth.AuthRequest.of(username)
                            .setPrincipal(new io.ddd4j.core.auth.AuthPrincipal().setLoginId(username).setUserId(username))
                            .extra("credential", password));
        } catch (io.ddd4j.core.exception.AuthenticationException failure) {
            throw new jakarta.ws.rs.NotAuthorizedException("invalid credentials");
        }
    }

    /**
     * 恢复前任全局引擎并销毁本实例资源。
     */
    @PreDestroy
    public void close() {
        if (current() == owned) {
            SecurityUtils.setSecurityManager(previous);
        }
        owned.destroy();
    }

    private static final class SampleRealm extends AuthorizingRealm {
        @Override
        protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
            String user = String.valueOf(token.getPrincipal());
            if (!Set.of("alice", "reader").contains(user)) {
                throw new UnknownAccountException("unknown sample account");
            }
            return new SimpleAuthenticationInfo(user, "sample-password", getName());
        }

        @Override
        protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
            var result = new SimpleAuthorizationInfo();
            result.setStringPermissions("alice".equals(principals.getPrimaryPrincipal()) ? Set.of("order:read", "order:write") : Set.of("order:read"));
            return result;
        }
    }
}
