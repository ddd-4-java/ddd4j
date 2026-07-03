package io.ddd4j.cache.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

import java.util.Objects;

/**
 * 基于 {@link InMemorySubject} 的内存版 {@link SubjectProvider}。
 *
 * <p>把所有 Subject 请求路由到同一个共享的 {@link InMemorySubject} 实例（适合单进程/单租户场景）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class InMemorySubjectProvider implements SubjectProvider {

    private final InMemorySubject subject;

    public InMemorySubjectProvider(InMemorySubject subject) {
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
    }

    @Override
    public Subject getSubject() {
        return subject;
    }

    @Override
    public Subject getSubject(String realm) {
        return subject;
    }
}
