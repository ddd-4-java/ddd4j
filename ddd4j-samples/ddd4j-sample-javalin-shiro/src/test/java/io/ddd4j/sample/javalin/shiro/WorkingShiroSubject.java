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

    public static final String SESSION_ATTR_PRINCIPAL = "authPrincipal";

    private final InMemoryUserRepository userRepository;

    public WorkingShiroSubject(InMemoryUserRepository userRepository) {
        this.userRepository = userRepository;
    }

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