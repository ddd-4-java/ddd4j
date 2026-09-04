package io.ddd4j.auth.security.subject;

import io.ddd4j.core.subject.Subject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SecuritySubjectProvider}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class SecuritySubjectProviderTest {

    @Test
    void getSubject_shouldReturnNonNullSubject() {
        SecuritySubjectProvider provider = new SecuritySubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isNotNull();
    }

    @Test
    void getSubject_shouldReturnSecuritySubjectInstance() {
        SecuritySubjectProvider provider = new SecuritySubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isInstanceOf(SecuritySubject.class);
    }

    @Test
    void getSubject_shouldReturnNewInstanceEachCall() {
        SecuritySubjectProvider provider = new SecuritySubjectProvider();

        Subject first = provider.getSubject();
        Subject second = provider.getSubject();

        assertThat(first).isNotSameAs(second);
    }
}
