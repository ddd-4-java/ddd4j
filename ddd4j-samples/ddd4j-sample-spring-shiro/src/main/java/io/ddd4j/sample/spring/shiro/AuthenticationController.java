package io.ddd4j.sample.spring.shiro;

import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.auth.shiro.subject.ShiroSubject;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectProvider;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 使用原生 Shiro 凭据匹配和 Servlet 会话的登录/退出入口。
 */
@RestController
public class AuthenticationController {
    /**
     * 解析请求认证主体的提供器或管理其注册生命周期的作用域。
     */
    private final SubjectProvider subjects;

    /**
     * @param subjects 外部 Boot 装配的 Shiro 主体适配器
     */
    public AuthenticationController(SubjectProvider subjects) {
        this.subjects = subjects;
    }

    /**
     * @param username 演示账号
     * @param password 演示密码
     * @return 原生 Servlet 会话 ID
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
     * @return 当前已认证主体标识
     */
    @RawResponse
    @GetMapping("/auth/me")
    public String me() {
        return subjects.getSubject().getLoginIdAsString();
    }

    /**
     * @return 注销结果
     */
    @RawResponse
    @PostMapping("/auth/logout")
    public String logout() {
        SecurityUtils.getSubject().logout();
        return "logged-out";
    }
}
