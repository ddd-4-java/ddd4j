package io.ddd4j.sample.spring.satoken;

import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.core.subject.SubjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 演示账号登录入口；认证会话、令牌校验与注销由原生 Sa-Token 管理。 */
@RestController
public class AuthenticationController {
    /** \u89e3\u6790\u8bf7\u6c42\u8ba4\u8bc1\u4e3b\u4f53\u7684\u63d0\u4f9b\u5668\u6216\u7ba1\u7406\u5176\u6ce8\u518c\u751f\u547d\u5468\u671f\u7684\u4f5c\u7528\u57df\u3002 */
    private final SubjectProvider subjects;
    /**
     * 初始化 <code>AuthenticationController</code> 使用的 <code>subjects</code>。
     *
     * @param subjects \u5916\u90e8 Boot \u88c5\u914d\u7684\u5b9e\u9645 Sa-Token \u4e3b\u4f53\u9002\u914d\u5668
     */
    public AuthenticationController(SubjectProvider subjects) {
        this.subjects = subjects;
    }
    /**
     * 执行 <code>login</code>，调用 <code>StpUtil.login</code>、<code>StpUtil.getTokenValue</code>。
     *
     * @param username \u6f14\u793a\u8d26\u53f7
     * @param password \u6f14\u793a\u5bc6\u7801
     * @return \u539f\u751f Sa-Token \u4ee4\u724c
     */
    @RawResponse
    @PostMapping("/auth/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        if (!("alice".equals(username) || "writer".equals(username)) || !"demo-password".equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid demo credentials");
        }
        StpUtil.login(username);
        return StpUtil.getTokenValue();
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
     * 执行 <code>logout</code>，调用 <code>StpUtil.logout</code>。
     *
     * @return \u6ce8\u9500\u7ed3\u679c
     */
    @RawResponse
    @PostMapping("/auth/logout")
    public String logout() {
        StpUtil.logout();
        return "logged-out";
    }
}
