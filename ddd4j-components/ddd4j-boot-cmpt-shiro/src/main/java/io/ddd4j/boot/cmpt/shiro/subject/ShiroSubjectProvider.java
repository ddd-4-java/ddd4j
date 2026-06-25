package io.ddd4j.boot.cmpt.shiro.subject;

import io.ddd4j.boot.core.subject.Subject;
import io.ddd4j.boot.core.subject.SubjectProvider;

public class ShiroSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new ShiroSubject();
    }

}
