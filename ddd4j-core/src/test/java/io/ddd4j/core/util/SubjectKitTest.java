package io.ddd4j.core.util;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SubjectKit} facade.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class SubjectKitTest {

    private Subject subject;
    private SubjectProvider provider;

    @BeforeEach
    void setUp() {
        subject = mock(Subject.class);
        provider = mock(SubjectProvider.class);
        when(provider.getSubject()).thenReturn(subject);
        resetStatic();
        SubjectKit.register(provider);
    }

    @AfterEach
    void tearDown() {
        resetStatic();
    }

    private void resetStatic() {
        SubjectKit.subjectProvider = null;
        SubjectKit.dataProvider = null;
        SubjectKit.strategy = null;
    }

    @Test
    void getSubject_shouldReturnRegisteredProviderSubject() {
        assertThat(SubjectKit.getSubject()).isSameAs(subject);
    }

    @Test
    void getSubject_shouldThrowWhenProviderNotRegistered() {
        SubjectKit.subjectProvider = null;

        assertThatThrownBy(SubjectKit::getSubject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SubjectProvider not registered");
    }

    @Test
    void getSubjectByRealm_shouldDelegateToProvider() {
        when(provider.getSubject("admin")).thenReturn(subject);

        Subject result = SubjectKit.getSubject("admin");

        assertThat(result).isSameAs(subject);
        verify(provider).getSubject("admin");
    }

    @Test
    void getPrincipal_shouldDelegateToSubject() {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("u1");
        when(subject.getPrincipal()).thenReturn(principal);

        AuthPrincipal resolved = SubjectKit.getPrincipal();

        assertThat(resolved).isSameAs(principal);
    }

    @Test
    void getPrincipal_withClass_shouldReturnNullOnTypeMismatch() {
        when(subject.getPrincipal()).thenReturn(new AuthPrincipal());

        CustomPrincipal result = SubjectKit.getPrincipal(CustomPrincipal.class);

        assertThat(result).isNull();
    }

    @Test
    void isLogin_shouldReflectSubjectAuthentication() {
        when(subject.isAuthenticated()).thenReturn(true);
        assertThat(Boolean.valueOf(SubjectKit.isLogin())).isTrue();

        when(subject.isAuthenticated()).thenReturn(false);
        assertThat(Boolean.valueOf(SubjectKit.isLogin())).isFalse();
    }

    @Test
    void hasPermission_shouldDelegateToSubject() {
        when(subject.isPermitted("user:add")).thenReturn(true);

        assertThat(SubjectKit.hasPermission("user:add")).isTrue();
        verify(subject).isPermitted("user:add");
    }

    @Test
    void hasRole_shouldDelegateToSubject() {
        when(subject.hasRole("admin")).thenReturn(true);

        assertThat(SubjectKit.hasRole("admin")).isTrue();
        verify(subject).hasRole("admin");
    }

    @Test
    void login_shouldDelegateAndReturnToken() {
        AuthRequest request = AuthRequest.of("u1");
        when(subject.login(request)).thenReturn("token-123");

        String token = SubjectKit.login(request);

        assertThat(token).isEqualTo("token-123");
        verify(subject).login(request);
    }

    @Test
    void logout_shouldDelegateToSubject() {
        SubjectKit.logout();

        verify(subject).logout();
    }

    @Test
    void getDataProvider_shouldReturnDefaultWhenNotRegistered() {
        SubjectKit.dataProvider = null;

        assertThat(SubjectKit.getDataProvider()).isNotNull();
    }

    @Test
    void getStrategy_shouldReturnDefaultWhenNotRegistered() {
        SubjectKit.strategy = null;

        assertThat(SubjectKit.getStrategy()).isNotNull();
    }

    static class CustomPrincipal extends AuthPrincipal {
    }
}
