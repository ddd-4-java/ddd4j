package io.ddd4j.sample.micronaut.shiro;

import io.micronaut.context.annotation.Context;
import jakarta.annotation.PreDestroy;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;

import java.util.Set;

/**
 * 单应用原生 Shiro Realm 和会话管理；固定凭证仅用于样例。
 */
@Context
public final class NativeAuth {
    /**
     * 当前示例安装并负责关闭的 Shiro 安全管理器。
     */
    private final DefaultSecurityManager owned;

    /**
     * 创建由 Micronaut 管理、绑定到请求作用域的原生引擎。
     */
    public NativeAuth() {
        owned = new DefaultSecurityManager(new SampleRealm());
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
        Subject subject = SecurityUtils.getSubject();
        UsernamePasswordToken token = new UsernamePasswordToken(username, password);
        try {
            subject.login(token);
        } catch (org.apache.shiro.authc.AuthenticationException failure) {
            throw new io.micronaut.http.exceptions.HttpStatusException(io.micronaut.http.HttpStatus.UNAUTHORIZED, "invalid credentials");
        } finally {
            token.clear();
        }
        return subject.getSession().getId().toString();
    }

    /**
     * 销毁本实例拥有的引擎和会话资源。
     */
    @PreDestroy
    public void close() {
        owned.destroy();
    }

    /**
     * 根据示例用户及订单权限执行原生 Shiro 认证与授权。
     */
    private static final class SampleRealm extends AuthorizingRealm {
        /**
         * 根据登录凭据查找供 Shiro 校验的身份信息。
         *
         * <p>此实现调用 <code>String.valueOf(token.getPrincipal())</code>。</p>
         *
         * @param token 待校验或用于定位会话的令牌
         * @return 此操作产生的<code>AuthenticationInfo</code>结果
         */
        @Override
        protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
            String user = String.valueOf(token.getPrincipal());
            if (!Set.of("alice", "reader").contains(user)) {
                throw new UnknownAccountException("unknown sample account");
            }
            return new SimpleAuthenticationInfo(user, "sample-password", getName());
        }

        /**
         * 加载 Shiro 用于权限和角色判定的信息。
         *
         * <p>此实现调用 <code>"alice".equals(principals.getPrimaryPrincipal())</code>。</p>
         *
         * @param principals 传给 <code>"alice".equals(principals.getPrimaryPrincipal())</code>
         * @return 实际返回<code>result</code>
         */
        @Override
        protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
            var result = new SimpleAuthorizationInfo();
            result.setStringPermissions("alice".equals(principals.getPrimaryPrincipal()) ? Set.of("order:read", "order:write") : Set.of("order:read"));
            return result;
        }
    }
}
