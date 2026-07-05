package io.ddd4j.sample.javalin.shiro;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;

/**
 * 测试专用 SubjectProvider：返回 {@link WorkingShiroSubject}，以绕开上游 ShiroSubject.login bug。
 */
public class WorkingSubjectProvider implements SubjectProvider {

    private final InMemoryUserRepository userRepository;

    public WorkingSubjectProvider(InMemoryUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Subject getSubject() {
        return new WorkingShiroSubject(userRepository);
    }

    @Override
    public Subject getSubject(String realm) {
        return new WorkingShiroSubject(userRepository);
    }
}