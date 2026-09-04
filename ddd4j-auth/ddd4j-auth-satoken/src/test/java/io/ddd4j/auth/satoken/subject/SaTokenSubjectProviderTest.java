package io.ddd4j.auth.satoken.subject;

import io.ddd4j.core.subject.Subject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SaTokenSubjectProvider}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class SaTokenSubjectProviderTest {

    @Test
    void getSubject_shouldReturnNonNullSubject() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isNotNull();
    }

    @Test
    void getSubject_shouldReturnSaTokenSubjectInstance() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isInstanceOf(SaTokenSubject.class);
    }

    @Test
    void getSubject_shouldReturnNewInstanceEachCall() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject first = provider.getSubject();
        Subject second = provider.getSubject();

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void getSubjectByRealm_shouldReturnNonNullSubject() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject subject = provider.getSubject("admin");

        assertThat(subject).isNotNull();
        assertThat(subject).isInstanceOf(SaTokenSubject.class);
    }

    @Test
    void getSubjectByRealm_withNullRealm_shouldStillReturnSubject() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject subject = provider.getSubject(null);

        assertThat(subject).isNotNull();
    }
}
