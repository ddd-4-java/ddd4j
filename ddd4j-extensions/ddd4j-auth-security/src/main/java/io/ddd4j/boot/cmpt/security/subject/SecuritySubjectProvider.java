package io.ddd4j.boot.cmpt.security.subject;

import io.ddd4j.boot.core.subject.Subject;
import io.ddd4j.boot.core.subject.SubjectProvider;

public class SecuritySubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new SecuritySubject();
    }

}
