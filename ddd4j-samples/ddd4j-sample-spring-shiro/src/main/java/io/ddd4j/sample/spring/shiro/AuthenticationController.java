package io.ddd4j.sample.spring.shiro;

import org.apache.shiro.SecurityUtils;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.auth.shiro.subject.ShiroSubject;
import org.apache.shiro.authc.UsernamePasswordToken;
import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.core.subject.SubjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 使用原生 Shiro 凭据匹配和 Servlet 会话的登录/退出入口。 */
@RestController
public class AuthenticationController {
    /** \u89e3\u6790\u8bf7\u6c42\u8ba4\u8bc1\u4e3b\u4f53\u7684\u63d0\u4f9b\u5668\u6216\u7ba1\u7406\u5176\u6ce8\u518c\u751f\u547d\u5468\u671f\u7684\u4f5c\u7528\u57df\u3002 */
    private final SubjectProvider subjects;
    /**
     * 初始化 <code>AuthenticationController</code> 使用的 <code>subjects</code>。
     *
     * @param subjects \u5916\u90e8 Boot \u88c5\u914d\u7684 Shiro \u4e3b\u4f53\u9002\u914d\u5668
     */
    public AuthenticationController(SubjectProvider subjects) {
        this.subjects = subjects;
    }
    /**
     * 执行 <code>login</code>，调用 <code>SecurityUtils.getSubject</code>、<code>subject.login</code>、<code>subject.getSession</code>。
     *
     * @param username \u6f14\u793a\u8d26\u53f7
     * @param password \u6f14\u793a\u5bc6\u7801
     * @return \u539f\u751f Servlet \u4f1a\u8bdd ID
     */
    @RawResponse
    @PostMapping("/auth/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        var subject = SecurityUtils.getSubject();
        subject.login(new UsernamePasswordToken(username, password));
        // 仅把原生 Realm 已认证的主体关联到既有 ddd4j 会话键，授权仍由 Realm 判定。
        subject.getSession().setAttribute(ShiroSubject.SESSION_KEY_PRINCIPAL,
                new AuthPrincipal().setLoginId(subject.getPrincipal()).setUserId(subject.getPrincipal().toString()));
        return subject.getSession().getId().toString();
    }
    /**
     * 执行 <code>me</code>，调用 <code>subjects.getSubject</code>。
     *
     * @return \u5f53\u524d\u5df2\u8ba4\u8bc1\u4e3b\u4f53\u6807\u8bc6
     */
    @RawResponse
    @GetMapping("/auth/me")
    public String me() {
        return subjects.getSubject().getLoginIdAsString();
    }
    /**
     * 执行 <code>logout</code>，调用 <code>SecurityUtils.getSubject</code>。
     *
     * @return \u6ce8\u9500\u7ed3\u679c
     */
    @RawResponse
    @PostMapping("/auth/logout")
    public String logout() {
        SecurityUtils.getSubject().logout();
        return "logged-out";
    }
}
