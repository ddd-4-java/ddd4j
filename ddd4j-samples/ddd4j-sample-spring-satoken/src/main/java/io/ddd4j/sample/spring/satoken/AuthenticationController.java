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
    private final SubjectProvider subjects;
    /**
     * @param subjects 外部 Boot 装配的实际 Sa-Token 主体适配器
     */
    public AuthenticationController(SubjectProvider subjects) {
        this.subjects = subjects;
    }
    /**
     * @param username 演示账号
     * @param password 演示密码
     * @return 原生 Sa-Token 令牌
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
        StpUtil.logout();
        return "logged-out";
    }
}
