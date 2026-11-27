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

    /** \u5f53\u524d\u7528\u4f8b\u4f7f\u7528\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002 */
    private Subject subject;
    /** \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u8ba4\u8bc1\u6216 SPI \u670d\u52a1\u7684\u7ec4\u4ef6\u3002 */
    private SubjectProvider provider;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>mock()</code>\u3001<code>provider.getSubject()</code>\u3001<code>resetStatic()</code>\u3001<code>SubjectKit.register()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        subject = mock(Subject.class);
        provider = mock(SubjectProvider.class);
        when(provider.getSubject()).thenReturn(subject);
        resetStatic();
        SubjectKit.register(provider);
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>resetStatic()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        resetStatic();
    }

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>SubjectKit.subjectProvider = null</code>\u3001<code>SubjectKit.dataProvider = null</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    private void resetStatic() {
        SubjectKit.subjectProvider = null;
        SubjectKit.dataProvider = null;
        SubjectKit.strategy = null;
    }

    /** \u9a8c\u8bc1<code>SubjectKit.getSubject()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>subject</code>\u3002 */
    @Test
    void getSubject_shouldReturnRegisteredProviderSubject() {
        assertThat(SubjectKit.getSubject()).isSameAs(subject);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void getSubject_shouldThrowWhenProviderNotRegistered() {
        SubjectKit.subjectProvider = null;

        assertThatThrownBy(SubjectKit::getSubject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SubjectProvider not registered");
    }

    /** \u9a8c\u8bc1<code>result</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>subject</code>\u3002 */
    @Test
    void getSubjectByRealm_shouldDelegateToProvider() {
        when(provider.getSubject("admin")).thenReturn(subject);

        Subject result = SubjectKit.getSubject("admin");

        assertThat(result).isSameAs(subject);
        verify(provider).getSubject("admin");
    }

    /** \u9a8c\u8bc1<code>resolved</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\u3002 */
    @Test
    void getPrincipal_shouldDelegateToSubject() {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("u1");
        when(subject.getPrincipal()).thenReturn(principal);

        AuthPrincipal resolved = SubjectKit.getPrincipal();

        assertThat(resolved).isSameAs(principal);
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getPrincipal_withClass_shouldReturnNullOnTypeMismatch() {
        when(subject.getPrincipal()).thenReturn(new AuthPrincipal());

        CustomPrincipal result = SubjectKit.getPrincipal(CustomPrincipal.class);

        assertThat(result).isNull();
    }

    /** \u9a8c\u8bc1<code>Boolean.valueOf(SubjectKit.isLogin())</code>\u4e3a\u771f\uff0c<code>Boolean.valueOf(SubjectKit.isLogin())</code>\u4e3a\u5047\u3002 */
    @Test
    void isLogin_shouldReflectSubjectAuthentication() {
        when(subject.isAuthenticated()).thenReturn(true);
        assertThat(Boolean.valueOf(SubjectKit.isLogin())).isTrue();

        when(subject.isAuthenticated()).thenReturn(false);
        assertThat(Boolean.valueOf(SubjectKit.isLogin())).isFalse();
    }

    /** \u9a8c\u8bc1<code>SubjectKit.hasPermission("user:add")</code>\u4e3a\u771f\u3002 */
    @Test
    void hasPermission_shouldDelegateToSubject() {
        when(subject.isPermitted("user:add")).thenReturn(true);

        assertThat(SubjectKit.hasPermission("user:add")).isTrue();
        verify(subject).isPermitted("user:add");
    }

    /** \u9a8c\u8bc1<code>SubjectKit.hasRole("admin")</code>\u4e3a\u771f\u3002 */
    @Test
    void hasRole_shouldDelegateToSubject() {
        when(subject.hasRole("admin")).thenReturn(true);

        assertThat(SubjectKit.hasRole("admin")).isTrue();
        verify(subject).hasRole("admin");
    }

    /** \u9a8c\u8bc1<code>token</code> \u7b49\u4e8e <code>"token-123"</code>\u3002 */
    @Test
    void login_shouldDelegateAndReturnToken() {
        AuthRequest request = AuthRequest.of("u1");
        when(subject.login(request)).thenReturn("token-123");

        String token = SubjectKit.login(request);

        assertThat(token).isEqualTo("token-123");
        verify(subject).login(request);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>SubjectKit.logout()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void logout_shouldDelegateToSubject() {
        SubjectKit.logout();

        verify(subject).logout();
    }

    /** \u9a8c\u8bc1<code>SubjectKit.getDataProvider()</code>\u975e\u7a7a\u3002 */
    @Test
    void getDataProvider_shouldReturnDefaultWhenNotRegistered() {
        SubjectKit.dataProvider = null;

        assertThat(SubjectKit.getDataProvider()).isNotNull();
    }

    /** \u9a8c\u8bc1<code>SubjectKit.getStrategy()</code>\u975e\u7a7a\u3002 */
    @Test
    void getStrategy_shouldReturnDefaultWhenNotRegistered() {
        SubjectKit.strategy = null;

        assertThat(SubjectKit.getStrategy()).isNotNull();
    }

    /** \u7528\u4e8e\u6d4b\u8bd5\u8eab\u4efd\u6620\u5c04\u662f\u5426\u4fdd\u7559\u81ea\u5b9a\u4e49\u4e3b\u4f53\u7c7b\u578b\u7684\u8ba4\u8bc1\u5bf9\u8c61\u3002 */
    static class CustomPrincipal extends AuthPrincipal {
    }
}
