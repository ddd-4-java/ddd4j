package io.ddd4j.sample.micronaut.shiro;
import org.apache.shiro.SecurityUtils;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Body;
import java.util.Map;
/** 原生登录、主体查询与退出入口；固定账号只用于样例。 */
@Controller("/auth")
public final class AuthenticationController {
 /** \u5904\u7406\u5f53\u524d\u793a\u4f8b\u5e94\u7528\u767b\u5f55\u72b6\u6001\u7684\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6\u3002 */
 private final NativeAuth auth;
 /** @param auth 原生会话装配 */
 public AuthenticationController(NativeAuth auth) { this.auth = auth; }
 /**
 * @param request 演示凭证
 * @return 会话令牌
 */
 @Post("/login") public Map<String,String> login(@Body Map<String,String> request) { return Map.of("token",auth.login(request.get("username"),request.get("password"))); }
 /** @return 已认证账号 */
 @Get("/me") public Map<String,String> me() { return Map.of("principal",SecurityUtils.getSubject().getPrincipal().toString()); }
 /** @return 退出结果 */
 @Post("/logout") public Map<String,Boolean> logout() { SecurityUtils.getSubject().logout(); return Map.of("loggedOut",true); }
}
