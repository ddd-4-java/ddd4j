package io.ddd4j.sample.quarkus.shiro.config;

import io.ddd4j.auth.shiro.subject.ShiroSubject;
import io.ddd4j.core.subject.Subject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Quarkus CDI 配置：把 Apache Shiro 的 {@link ShiroSubject} 暴露为 CDI Bean，
 * 以便 {@code ddd4j-runtime-quarkus} 的 {@code CdiSubjectProvider} 能通过
 * CDI {@code Instance<Subject>} 查找并委托给 Shiro 底层。
 *
 * <p>这是 Quarkus + Shiro 集成必须的桥接：Shiro 本身不感知 CDI，
 * 由本类显式把 Shiro 实现注册成 CDI 可发现的 Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class SubjectConfig {

    /**
     * Shiro 实现的 ddd4j {@link Subject}。
     */
    @Produces
    @ApplicationScoped
    public Subject shiroSubject() {
        return new ShiroSubject();
    }

}