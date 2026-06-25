package io.ddd4j.core.subject;

public interface SubjectProvider {

    default Subject getSubject() {
        return SubjectKit.getSubject();
    }

}
