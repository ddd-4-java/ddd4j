package io.ddd4j.boot.cmpt.satoken.subject;

import io.ddd4j.boot.core.subject.Subject;
import io.ddd4j.boot.core.subject.SubjectProvider;

public class SaTokenSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new SaTokenSubject();
    }

}
