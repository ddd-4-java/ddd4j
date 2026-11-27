/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.cache.subject;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.auth.event.AuthFailedEvent;
import io.ddd4j.core.auth.event.AuthSucceededEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** \u9a8c\u8bc1\u5185\u5b58\u4e3b\u4f53\u767b\u5f55\u6210\u529f\u4e0e\u5931\u8d25\u65f6\u53d1\u5e03\u7684\u8ba4\u8bc1\u4e8b\u4ef6\u53ca\u5f53\u524d\u4e3b\u4f53\u3002 */
class InMemorySubjectAuthEventContractTest {

    /** \u5f53\u524d\u6d4b\u8bd5\u671f\u95f4\u6536\u96c6\u7684\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u6bd4\u8f83\u53d1\u5e03\u5185\u5bb9\u6216\u987a\u5e8f\u3002 */
    private final List<Object> events = new ArrayList<>();
    /** \u5f53\u524d\u7528\u4f8b\u4e2d\u8d1f\u8d23\u5185\u5b58\u767b\u5f55\u548c\u53d1\u5e03\u8ba4\u8bc1\u4e8b\u4ef6\u7684\u4e3b\u4f53\u3002 */
    private InMemorySubject subject;

    /** \u6e05\u9664\u5f53\u524d\u7ebf\u7a0b\u7684\u4e3b\u4f53\u5e76\u6ce8\u9500\u6d4b\u8bd5\u4f7f\u7528\u7684\u4f1a\u8bdd\u548c\u8d26\u53f7\u72b6\u6001\u7f13\u5b58\u3002 */
    @AfterEach
    void tearDown() {
        if (Objects.nonNull(subject)) {
            subject.clear();
        }
        for (String name : Arrays.asList(
                InMemorySubject.PRINCIPALS_BY_TOKEN_CACHE,
                InMemorySubject.PRINCIPALS_BY_LOGIN_ID_CACHE,
                InMemorySubject.TOKEN_BY_LOGIN_ID_CACHE,
                InMemorySubject.DISABLED_UNTIL_CACHE,
                InMemorySubject.CURRENT_TOKEN_CACHE)) {
            CacheKit.unregister(name);
        }
    }

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>events.get(0)</code> \u7684\u7c7b\u578b\u4e3a <code>AuthSucceededEvent.class</code>\uff0c<code>current</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>event.principal()</code>\u3002 */
    @Test
    void loginSuccessPublishesCurrentPrincipalAndToken() {
        subject = new InMemorySubject(events::add);
        AuthRequest request = AuthRequest.of("account-1");

        String token = subject.login(request);

        assertThat(token).isNotBlank();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AuthSucceededEvent.class);
        AuthSucceededEvent event = (AuthSucceededEvent) events.get(0);
        assertThat(event.request()).isSameAs(request);
        assertThat(event.token()).isEqualTo(token);
        assertThat(event.principal().getLoginId()).isEqualTo("account-1");
        assertThat(event.occurredAt()).isNotNull();
        AuthPrincipal current = subject.getPrincipal();
        assertThat(current).isSameAs(event.principal());
    }

    /** \u9a8c\u8bc1<code>subject.login(request)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>subject.isAuthenticated()</code>\u4e3a\u5047\u3002 */
    @Test
    void invalidLoginPublishesFailureAndKeepsSubjectAnonymous() {
        subject = new InMemorySubject(events::add);
        AuthRequest request = AuthRequest.of(null);

        assertThatThrownBy(() -> subject.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("loginId must not be null");

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AuthFailedEvent.class);
        AuthFailedEvent event = (AuthFailedEvent) events.get(0);
        assertThat(event.request()).isSameAs(request);
        assertThat(event.reason()).isEqualTo("loginId must not be null");
        assertThat(event.occurredAt()).isNotNull();
        assertThat(subject.isAuthenticated()).isFalse();
    }
}
