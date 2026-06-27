package io.ddd4j.auth.satoken.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SaTokenSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new SaTokenSubject();
    }

}
