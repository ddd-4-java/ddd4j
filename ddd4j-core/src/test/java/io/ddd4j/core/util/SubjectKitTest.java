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

    /**
     * 当前用例使用的认证主体。
     */
    private Subject subject;
    /**
     * 为当前用例提供认证或 SPI 服务的组件。
     */
    private SubjectProvider provider;

    /**
     * 测试执行前执行 <code>mock()</code>、<code>provider.getSubject()</code>、<code>resetStatic()</code>、<code>SubjectKit.register()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        subject = mock(Subject.class);
        provider = mock(SubjectProvider.class);
        when(provider.getSubject()).thenReturn(subject);
        resetStatic();
        SubjectKit.register(provider);
    }

    /**
     * 测试结束后执行 <code>resetStatic()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        resetStatic();
    }

    /**
     * 根据本次调用更新 <code>SubjectKit.subjectProvider = null</code>、<code>SubjectKit.dataProvider = null</code>，用于后续测试步骤。
     */
    private void resetStatic() {
        SubjectKit.subjectProvider = null;
        SubjectKit.dataProvider = null;
        SubjectKit.strategy = null;
    }

    /**
     * 验证<code>SubjectKit.getSubject()</code> 指向同一实例 <code>subject</code>。
     */
    @Test
    void getSubject_shouldReturnRegisteredProviderSubject() {
        assertThat(SubjectKit.getSubject()).isSameAs(subject);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void getSubject_shouldThrowWhenProviderNotRegistered() {
        SubjectKit.subjectProvider = null;

        assertThatThrownBy(SubjectKit::getSubject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SubjectProvider not registered");
    }

    /**
     * 验证<code>result</code> 指向同一实例 <code>subject</code>。
     */
    @Test
    void getSubjectByRealm_shouldDelegateToProvider() {
        when(provider.getSubject("admin")).thenReturn(subject);

        Subject result = SubjectKit.getSubject("admin");

        assertThat(result).isSameAs(subject);
        verify(provider).getSubject("admin");
    }

    /**
     * 验证<code>resolved</code> 指向同一实例 <code>principal</code>。
     */
    @Test
    void getPrincipal_shouldDelegateToSubject() {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("u1");
        when(subject.getPrincipal()).thenReturn(principal);

        AuthPrincipal resolved = SubjectKit.getPrincipal();

        assertThat(resolved).isSameAs(principal);
    }

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void getPrincipal_withClass_shouldReturnNullOnTypeMismatch() {
        when(subject.getPrincipal()).thenReturn(new AuthPrincipal());

        CustomPrincipal result = SubjectKit.getPrincipal(CustomPrincipal.class);

        assertThat(result).isNull();
    }

    /**
     * 验证<code>Boolean.valueOf(SubjectKit.isLogin())</code>为真，<code>Boolean.valueOf(SubjectKit.isLogin())</code>为假。
     */
    @Test
    void isLogin_shouldReflectSubjectAuthentication() {
        when(subject.isAuthenticated()).thenReturn(true);
        assertThat(Boolean.valueOf(SubjectKit.isLogin())).isTrue();

        when(subject.isAuthenticated()).thenReturn(false);
        assertThat(Boolean.valueOf(SubjectKit.isLogin())).isFalse();
    }

    /**
     * 验证<code>SubjectKit.hasPermission("user:add")</code>为真。
     */
    @Test
    void hasPermission_shouldDelegateToSubject() {
        when(subject.isPermitted("user:add")).thenReturn(true);

        assertThat(SubjectKit.hasPermission("user:add")).isTrue();
        verify(subject).isPermitted("user:add");
    }

    /**
     * 验证<code>SubjectKit.hasRole("admin")</code>为真。
     */
    @Test
    void hasRole_shouldDelegateToSubject() {
        when(subject.hasRole("admin")).thenReturn(true);

        assertThat(SubjectKit.hasRole("admin")).isTrue();
        verify(subject).hasRole("admin");
    }

    /**
     * 验证<code>token</code> 等于 <code>"token-123"</code>。
     */
    @Test
    void login_shouldDelegateAndReturnToken() {
        AuthRequest request = AuthRequest.of("u1");
        when(subject.login(request)).thenReturn("token-123");

        String token = SubjectKit.login(request);

        assertThat(token).isEqualTo("token-123");
        verify(subject).login(request);
    }

    /**
     * 在测试框架中执行 <code>SubjectKit.logout()</code>，验证该场景。
     */
    @Test
    void logout_shouldDelegateToSubject() {
        SubjectKit.logout();

        verify(subject).logout();
    }

    /**
     * 验证<code>SubjectKit.getDataProvider()</code>非空。
     */
    @Test
    void getDataProvider_shouldReturnDefaultWhenNotRegistered() {
        SubjectKit.dataProvider = null;

        assertThat(SubjectKit.getDataProvider()).isNotNull();
    }

    /**
     * 验证<code>SubjectKit.getStrategy()</code>非空。
     */
    @Test
    void getStrategy_shouldReturnDefaultWhenNotRegistered() {
        SubjectKit.strategy = null;

        assertThat(SubjectKit.getStrategy()).isNotNull();
    }

    /**
     * 用于测试身份映射是否保留自定义主体类型的认证对象。
     */
    static class CustomPrincipal extends AuthPrincipal {
    }
}
