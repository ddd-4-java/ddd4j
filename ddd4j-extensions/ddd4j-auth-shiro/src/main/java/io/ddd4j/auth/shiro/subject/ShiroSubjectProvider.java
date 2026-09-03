package io.ddd4j.auth.shiro.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * Shiro Subject 工厂（纯 Java，零 Spring 依赖）。
 *
 * <p>注意：本实现的 {@link #getSubject()} 直接 new {@link ShiroSubject}，
 * <b>不</b>调用 {@link io.ddd4j.core.util.SubjectKit#getSubject()}（避免死循环）。
 * 这与 {@code SubjectProvider} 接口的 default 实现不同，必须在子类重写。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ShiroSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new ShiroSubject();
    }

    @Override
    public Subject getSubject(String realm) {
        // Shiro 多 Realm 场景由 SecurityManager 配置，此处返回默认 Subject
        return new ShiroSubject();
    }

}
