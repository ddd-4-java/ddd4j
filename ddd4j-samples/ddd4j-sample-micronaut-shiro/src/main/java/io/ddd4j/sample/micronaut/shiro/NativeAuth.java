package io.ddd4j.sample.micronaut.shiro;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PreDestroy;
import org.apache.shiro.SecurityUtils;
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
import org.apache.shiro.subject.Subject;
import java.util.Set;
/** 单应用原生 Shiro Realm 和会话管理；固定凭证仅用于样例。 */
@Context
public final class NativeAuth {
 private final DefaultSecurityManager owned;
 /** 创建由 Micronaut 管理、绑定到请求作用域的原生引擎。 */
 public NativeAuth() {
  owned = new DefaultSecurityManager(new SampleRealm());
 }
 /** @return 本样例拥有的引擎 */
 public SecurityManager manager() { return owned; }
 /**
 * @param username 账号
 * @param password 凭证
 * @return 原生会话标识
 */
 public String login(String username,String password) {
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
 /** 销毁本实例拥有的引擎和会话资源。 */
 @PreDestroy public void close() { owned.destroy(); }
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
