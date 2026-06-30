package io.ddd4j.sample.auth.multilogin.subject;

import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.AuthRequest;
import io.ddd4j.sample.auth.multilogin.event.LoginFailedEvent;
import io.ddd4j.sample.auth.multilogin.event.LoginSucceededEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySubjectTest {

    private final List<Object> events = new ArrayList<>();
    private final ApplicationEventPublisher eventPublisher = event -> {
        if (!(event instanceof ApplicationEvent)) {
            events.add(event);
        }
    };

    @Test
    void loginPublishesSuccessEventAndBindsPrincipal() {
        InMemorySubject subject = new InMemorySubject(eventPublisher);
        AuthPrincipal principal = new AuthPrincipal().setLoginId("13800138000").setUserId("u-1");
        AuthRequest request = AuthRequest.of("13800138000")
                .setPrincipal(principal)
                .setRealm("mobile")
                .extra("loginScene", "phone")
                .extra("verificationCode", "123456");

        String token = subject.login(request);
        AuthPrincipal currentPrincipal = subject.getPrincipal();

        assertThat(token).startsWith("mobile:");
        assertThat(subject.isAuthenticated()).isTrue();
        assertThat(currentPrincipal).isSameAs(principal);
        assertThat(events).hasOnlyElementsOfType(LoginSucceededEvent.class);
    }

    @Test
    void invalidPhoneCodePublishesFailureEvent() {
        InMemorySubject subject = new InMemorySubject(eventPublisher);
        AuthRequest request = AuthRequest.of("13800138000")
                .setRealm("mobile")
                .extra("loginScene", "phone")
                .extra("verificationCode", "000000");

        assertThatThrownBy(() -> subject.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid phone verification code");
        assertThat(events).hasOnlyElementsOfType(LoginFailedEvent.class);
    }
}
