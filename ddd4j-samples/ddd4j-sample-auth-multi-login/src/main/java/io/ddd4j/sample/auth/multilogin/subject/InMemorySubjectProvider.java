package io.ddd4j.sample.auth.multilogin.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

public class InMemorySubjectProvider implements SubjectProvider {

    private final InMemorySubject subject;

    public InMemorySubjectProvider(InMemorySubject subject) {
        this.subject = subject;
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
