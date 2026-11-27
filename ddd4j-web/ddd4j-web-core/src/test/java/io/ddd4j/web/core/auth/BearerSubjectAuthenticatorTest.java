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

/** \u9a8c\u8bc1 <code>BearerSubjectAuthenticator</code> \u5728<code>authenticate</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@ExtendWith(MockitoExtension.class)
class BearerSubjectAuthenticatorTest {

    /** \u7528\u4e8e\u9694\u79bb <code>Subject</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private Subject subject;

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>result</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\u3002 */
    @Test
    void authenticateReturnsPrincipalOfVerifiedSubject() {
        AuthPrincipal principal = new AuthPrincipal().setUserId("user-1");
        when(subject.verify("valid-token")).thenReturn(principal);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        AuthPrincipal result = new BearerSubjectAuthenticator().authenticate("Bearer valid-token");

        assertSame(principal, result);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>WebStatusException.class</code>\u3002 */
    @Test
    void authenticateRejectsInvalidToken() {
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        assertThrows(WebStatusException.class,
                () -> new BearerSubjectAuthenticator().authenticate("Bearer invalid"));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>WebStatusException.class</code>\u3002 */
    @Test
    void authenticateRejectsMissingProvider() {
        assertThrows(WebStatusException.class,
                () -> new BearerSubjectAuthenticator().authenticate("Bearer valid-token"));
    }

    /**
     * \u521b\u5efa <code>new SubjectProvider</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param subject \u7528\u4e8e\u9694\u79bb <code>Subject</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SubjectProvider(){ &#64;Override public Subject getSubject() { return subject; } }</code>
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
