package io.ddd4j.boot.core.subject;

public interface SubjectProvider {

    default Subject getSubject() {
        return SubjectKit.getSubject();
    }

}
