package io.ddd4j.sample.quarkus.cqrs.spi;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Quarkus CQRS 示例使用的匿名主体提供器。
 */
@ApplicationScoped
public class AnonymousSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return null;
    }
}
