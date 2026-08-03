package io.ddd4j.core.util;

import io.ddd4j.core.subject.SubjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectKitRegistrationScopeTest {

    private final SubjectProvider originalProvider = SubjectKit.subjectProvider;

    @AfterEach
    void restoreSubjectProvider() {
        SubjectKit.register(originalProvider);
    }

    @Test
    void shouldRestorePreviousProviderOnClose() {
        SubjectProvider previous = new SubjectProvider() {
        };
        SubjectProvider current = new SubjectProvider() {
        };
        SubjectKit.register(previous);

        try (SubjectKitRegistrationScope scope = new SubjectKitRegistrationScope(current)) {
            scope.start();
            assertThat(SubjectKit.subjectProvider).isSameAs(current);
        }

        assertThat(SubjectKit.subjectProvider).isSameAs(previous);
    }

    @Test
    void shouldNotOverwriteProviderReplacedAfterStart() {
        SubjectProvider current = new SubjectProvider() {
        };
        SubjectProvider replacement = new SubjectProvider() {
        };
        SubjectKitRegistrationScope scope = new SubjectKitRegistrationScope(current);
        scope.start();
        SubjectKit.register(replacement);

        scope.close();

        assertThat(SubjectKit.subjectProvider).isSameAs(replacement);
    }
}
