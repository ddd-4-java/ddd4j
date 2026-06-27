package io.ddd4j.core.subject;

import io.ddd4j.core.util.SubjectKit;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface SubjectProvider {

    default Subject getSubject() {
        return SubjectKit.getSubject();
    }

}
