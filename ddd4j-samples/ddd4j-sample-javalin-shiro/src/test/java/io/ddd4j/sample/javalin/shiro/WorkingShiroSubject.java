/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.javalin.shiro;

import io.ddd4j.auth.shiro.subject.ShiroSubject;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

import java.util.Objects;

/**
 * 测试专用 ShiroSubject 包装：覆盖父类 login(AuthRequest) 与 getPrincipal() 行为，
 * 以修复上游 bug。
 *
 * <p>上游 bug 列表：
 * <ul>
 *   <li>{@link ShiroSubject#login(AuthRequest)} 把 {@code request.getPrincipal()}
 *       当作密码传给 Shiro 的 UsernamePasswordToken — 这与 {@code AuthRequest} 的契约不符，
 *       必然导致 Realm 的密码校验失败。</li>
 *   <li>{@link ShiroSubject#getPrincipal()} 从 {@code subject.getPrincipal()} 取值，
 *       但 Shiro Realm 写入的是 RBAC User 对象，不是 AuthPrincipal，导致业务侧拿到 null。</li>
 * </ul>
 *
 * <p>本测试专用子类额外接收一个 {@link InMemoryUserRepository}，按 loginId 查询真实密码；
 * 并在 session 中保存 AuthPrincipal，再在 {@link #getPrincipal()} 中从 session 取出。</p>
 *
 * <p><b>仅用于测试目的，不得在生产代码中复用。</b></p>
 */
public class WorkingShiroSubject extends ShiroSubject {

    /** \u5728\u539f\u751f\u6d4b\u8bd5\u4f1a\u8bdd\u4e2d\u5b58\u50a8\u4e3b\u4f53\u4fe1\u606f\u7684\u5c5e\u6027\u952e\u3002 */
    public static final String SESSION_ATTR_PRINCIPAL = "authPrincipal";

    /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u7528\u6237\u7684\u4ed3\u50a8\u3002 */
    private final InMemoryUserRepository userRepository;

    /**
     * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>WorkingShiroSubject</code> \u5b9e\u4f8b\u3002
     * @param userRepository \u8bfb\u53d6\u548c\u4fdd\u5b58\u7528\u6237\u7684\u4ed3\u50a8
     */
    public WorkingShiroSubject(InMemoryUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 以传入的凭据启动认证会话。
     *
     * <p>测试夹具会调用 <code>SecurityUtils.getSubject()</code>、<code>String.valueOf(request.getLoginId())</code>。</p>
     *
     * @param request 框架传入的当前请求
     * @return 实际返回<code>Objects.nonNull(subject.getSession()) ? subject.getSession().getId().toString() : null</code>
     */
    @Override
    public String login(AuthRequest request) {
        Subject subject = SecurityUtils.getSubject();
        String loginId = String.valueOf(request.getLoginId());
        String password = userRepository.findByLoginId(loginId)
                .map(u -> u.password())
                .orElse("");
        UsernamePasswordToken token = new UsernamePasswordToken(loginId, password);
        subject.login(token);
        // 把 RBAC User 和 AuthPrincipal 都存到 session
        if (Objects.nonNull(subject.getSession())) {
            userRepository.findByLoginId(loginId)
                    .ifPresent(u -> subject.getSession().setAttribute("user", u));
            if (Objects.nonNull(request.getPrincipal())) {
                subject.getSession().setAttribute(SESSION_ATTR_PRINCIPAL, request.getPrincipal());
            }
        }
        return Objects.nonNull(subject.getSession()) ? subject.getSession().getId().toString() : null;
    }

    /**
     * 读取当前会话的认证主体。
     *
     * <p>测试夹具会调用 <code>SecurityUtils.getSubject()</code>、<code>subject.getSession()</code>。</p>
     *
     * @param <T> 此操作处理的值或结果的 Java 类型
     * @return 根据执行路径返回<code>(T)p</code>、<code>super.getPrincipal()</code>
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends AuthPrincipal> T getPrincipal() {
        // 优先从 session 取 AuthPrincipal（解决上游 getPrincipal() 的 bug）
        try {
            Subject subject = SecurityUtils.getSubject();
            if (Objects.nonNull(subject) && Objects.nonNull(subject.getSession())) {
                Object p = subject.getSession().getAttribute(SESSION_ATTR_PRINCIPAL);
                if (p instanceof AuthPrincipal) {
                    return (T) p;
                }
            }
        } catch (Exception ignored) {
        }
        // 退回到父类实现
        return super.getPrincipal();
    }
}
