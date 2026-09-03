package io.ddd4j.web.core.auth;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.error.WebStatusException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BearerSubjectAuthenticatorTest {

    @Mock
    private Subject subject;

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void authenticateReturnsPrincipalOfVerifiedSubject() {
        AuthPrincipal principal = new AuthPrincipal().setUserId("user-1");
        when(subject.verify("valid-token")).thenReturn(principal);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        AuthPrincipal result = new BearerSubjectAuthenticator().authenticate("Bearer valid-token");

        assertSame(principal, result);
    }

    @Test
    void authenticateRejectsInvalidToken() {
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        assertThrows(WebStatusException.class,
                () -> new BearerSubjectAuthenticator().authenticate("Bearer invalid"));
    }

    @Test
    void authenticateRejectsMissingProvider() {
        assertThrows(WebStatusException.class,
                () -> new BearerSubjectAuthenticator().authenticate("Bearer valid-token"));
    }

    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }
}
