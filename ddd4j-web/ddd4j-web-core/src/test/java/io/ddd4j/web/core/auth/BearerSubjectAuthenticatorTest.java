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

/**
 * 验证 <code>BearerSubjectAuthenticator</code> 在<code>authenticate</code>等操作中的行为契约。
 */
@ExtendWith(MockitoExtension.class)
class BearerSubjectAuthenticatorTest {

    /**
     * 用于隔离 <code>Subject</code> 调用并记录交互的测试替身。
     */
    @Mock
    private Subject subject;

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    /**
     * 验证<code>result</code> 与…为同一实例 <code>principal</code>。
     */
    @Test
    void authenticateReturnsPrincipalOfVerifiedSubject() {
        AuthPrincipal principal = new AuthPrincipal().setUserId("user-1");
        when(subject.verify("valid-token")).thenReturn(principal);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        AuthPrincipal result = new BearerSubjectAuthenticator().authenticate("Bearer valid-token");

        assertSame(principal, result);
    }

    /**
     * 验证被测调用 抛出 <code>WebStatusException.class</code>。
     */
    @Test
    void authenticateRejectsInvalidToken() {
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        assertThrows(WebStatusException.class,
                () -> new BearerSubjectAuthenticator().authenticate("Bearer invalid"));
    }

    /**
     * 验证被测调用 抛出 <code>WebStatusException.class</code>。
     */
    @Test
    void authenticateRejectsMissingProvider() {
        assertThrows(WebStatusException.class,
                () -> new BearerSubjectAuthenticator().authenticate("Bearer valid-token"));
    }

    /**
     * 创建 <code>new SubjectProvider</code> 供当前测试路径调用。
     *
     * @param subject 用于隔离 <code>Subject</code> 调用并记录交互的测试替身
     * @return 当前测试使用的结果，取自 <code>new SubjectProvider(){ &#64;Override public Subject getSubject() { return subject; } }</code>
     */
    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }
}
