package io.ddd4j.auth.security.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * Spring Security Subject 工厂。
 *
 * <p>默认返回 {@link SecuritySubject} 实例。
 * 本模块<b>允许</b> Spring 依赖（Spring Security 本就是 Spring 生态组件）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class SecuritySubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new SecuritySubject();
    }

}
