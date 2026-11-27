package io.ddd4j.auth.spring;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectRegistrarTest {

    @Test
    void shouldRegisterSubjectProviderAfterInitialization() {
        SubjectProvider provider = new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return null;
            }
        };

        Object result = new SubjectRegistrar().postProcessAfterInitialization(provider, "subjectProvider");

        assertThat(result).isSameAs(provider);
        assertThat(SubjectKit.subjectProvider).isSameAs(provider);
    }
}
