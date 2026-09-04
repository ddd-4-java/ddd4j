package io.ddd4j.auth.shiro.subject;

import io.ddd4j.core.subject.Subject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ShiroSubjectProvider}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ShiroSubjectProviderTest {

    @Test
    void getSubject_shouldReturnNonNullSubject() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isNotNull();
    }

    @Test
    void getSubject_shouldReturnShiroSubjectInstance() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isInstanceOf(ShiroSubject.class);
    }

    @Test
    void getSubject_shouldReturnNewInstanceEachCall() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject first = provider.getSubject();
        Subject second = provider.getSubject();

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void getSubjectByRealm_shouldReturnNonNullSubject() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject subject = provider.getSubject("admin");

        assertThat(subject).isNotNull();
        assertThat(subject).isInstanceOf(ShiroSubject.class);
    }

    @Test
    void getSubjectByRealm_withNullRealm_shouldStillReturnSubject() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject subject = provider.getSubject(null);

        assertThat(subject).isNotNull();
    }
}
