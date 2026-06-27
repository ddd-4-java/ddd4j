package io.ddd4j.core.subject;

import io.ddd4j.core.util.SubjectKit;

public interface SubjectProvider {

    default Subject getSubject() {
        return SubjectKit.getSubject();
    }

}
