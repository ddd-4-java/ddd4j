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

/**
 * 验证内存主体登录成功与失败时发布的认证事件及当前主体。
 */
class InMemorySubjectAuthEventContractTest {

    /**
     * 当前测试期间收集的领域事件，供断言比较发布内容或顺序。
     */
    private final List<Object> events = new ArrayList<>();
    /**
     * 当前用例中负责内存登录和发布认证事件的主体。
     */
    private InMemorySubject subject;

    /**
     * 清除当前线程的主体并注销测试使用的会话和账号状态缓存。
     */
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

    /**
     * 验证<code>events</code> 的大小为 <code>1</code>，<code>events.get(0)</code> 的类型为 <code>AuthSucceededEvent.class</code>，<code>current</code> 指向同一实例 <code>event.principal()</code>。
     */
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

    /**
     * 验证<code>subject.login(request)</code> 抛出 <code>IllegalArgumentException.class</code>，<code>events</code> 的大小为 <code>1</code>，<code>subject.isAuthenticated()</code>为假。
     */
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
