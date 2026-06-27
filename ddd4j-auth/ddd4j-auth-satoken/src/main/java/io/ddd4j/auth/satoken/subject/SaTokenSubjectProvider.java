package io.ddd4j.auth.satoken.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

public class SaTokenSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new SaTokenSubject();
    }

}
