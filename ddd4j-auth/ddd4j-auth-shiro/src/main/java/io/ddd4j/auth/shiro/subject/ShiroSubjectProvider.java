package io.ddd4j.auth.shiro.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ShiroSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new ShiroSubject();
    }

}
