package io.ddd4j.sample.auth.multilogin.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.auth.event.AuthFailedEvent;
import io.ddd4j.core.auth.event.AuthSucceededEvent;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.cache.subject.InMemorySubject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 测试 cache 版 {@link InMemorySubject}：事件发布走 ddd4j 通用 {@link DomainEvent}。
 */
class InMemorySubjectTest {

    private final List<Object> events = new ArrayList<>();
    private final DomainEventPublisher eventPublisher = new DomainEventPublisher() {
        @Override
        public <T> void publish(DomainEvent<T> event) {
            events.add(event.source());
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
        assertThat(events).hasOnlyElementsOfType(AuthSucceededEvent.class);
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
        assertThat(events).hasOnlyElementsOfType(AuthFailedEvent.class);
    }
}
