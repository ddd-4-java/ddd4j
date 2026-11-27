package io.ddd4j.sample.micronaut.shiro;
import com.google.common.collect.ImmutableSet;
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
 /** \u5f53\u524d\u793a\u4f8b\u5b89\u88c5\u5e76\u8d1f\u8d23\u5173\u95ed\u7684 Shiro \u5b89\u5168\u7ba1\u7406\u5668\u3002 */
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
 /** \u6839\u636e\u793a\u4f8b\u7528\u6237\u53ca\u8ba2\u5355\u6743\u9650\u6267\u884c\u539f\u751f Shiro \u8ba4\u8bc1\u4e0e\u6388\u6743\u3002 */
 private static final class SampleRealm extends AuthorizingRealm {
  /**
   * 根据登录凭据查找供 Shiro 校验的身份信息。
   *
   * <p>此实现调用 <code>String.valueOf(token.getPrincipal())</code>。</p>
   *
   * @param token 待校验或用于定位会话的令牌
   * @return 此操作产生的<code>AuthenticationInfo</code>结果
   */
  @Override protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
   String user = String.valueOf(token.getPrincipal());
   if (!ImmutableSet.of("alice","reader").contains(user)) { throw new UnknownAccountException("unknown sample account"); }
   return new SimpleAuthenticationInfo(user,"sample-password",getName());
  }
  /**
   * 加载 Shiro 用于权限和角色判定的信息。
   *
   * <p>此实现调用 <code>"alice".equals(principals.getPrimaryPrincipal())</code>。</p>
   *
   * @param principals 传给 <code>"alice".equals(principals.getPrimaryPrincipal())</code>
   * @return 实际返回<code>result</code>
   */
  @Override protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
   SimpleAuthorizationInfo result = new SimpleAuthorizationInfo();
   result.setStringPermissions("alice".equals(principals.getPrimaryPrincipal()) ? ImmutableSet.of("order:read","order:write") : ImmutableSet.of("order:read"));
   return result;
  }
 }
}
