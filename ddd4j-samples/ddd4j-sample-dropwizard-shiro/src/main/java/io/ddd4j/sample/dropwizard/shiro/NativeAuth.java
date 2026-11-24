package io.ddd4j.sample.dropwizard.shiro;



import io.ddd4j.web.core.error.WebStatusException;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import java.util.Objects;
import java.util.Set;
/** 单应用原生 Shiro Realm 和会话管理；固定凭证仅用于样例。
 */

public class NativeAuth implements AutoCloseable {
 private final DefaultSecurityManager owned = new DefaultSecurityManager(new SampleRealm());
 /**
 * @return 本样例拥有的引擎
 */
 public SecurityManager manager() { return owned; }
 /**
 * @param username 账号
 * @param password 凭证
 * @return 原生会话标识
 */
 public String login(String username,String password) {
  if (Objects.isNull(username)) {
   throw new WebStatusException(401, "invalid credentials");
  }
  try { return new io.ddd4j.auth.shiro.subject.ShiroSubjectProvider().getSubject().login(
    io.ddd4j.core.auth.AuthRequest.of(username)
      .setPrincipal(new io.ddd4j.core.auth.AuthPrincipal().setLoginId(username).setUserId(username))
      .extra("credential",password));
  } catch (io.ddd4j.core.exception.AuthenticationException failure) { throw new WebStatusException(401, "invalid credentials"); }
 }
 /** 销毁本实例创建的引擎和会话资源。
 */
 @Override public void close() { owned.destroy(); }
 private static final class SampleRealm extends AuthorizingRealm {
  @Override protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
   String user = String.valueOf(token.getPrincipal());
   if (!Set.of("alice","reader").contains(user)) { throw new UnknownAccountException("unknown sample account"); }
   return new SimpleAuthenticationInfo(user,"sample-password",getName());
  }
  @Override protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
   var result = new SimpleAuthorizationInfo();
   result.setStringPermissions("alice".equals(principals.getPrimaryPrincipal()) ? Set.of("order:read","order:write") : Set.of("order:read"));
   return result;
  }
 }
}
