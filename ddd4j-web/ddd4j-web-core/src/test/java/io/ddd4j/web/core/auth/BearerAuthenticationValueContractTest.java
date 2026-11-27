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

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>authentication.token()</code>、<code>authentication.getToken()</code>、<code>authentication.principal()</code> 验证 <code>BearerAuthenticationValueContract</code> 的运行行为。
 */
class BearerAuthenticationValueContractTest {

    /**
     * 验证<code>authentication.token()</code> 等于 <code>authentication.getToken()</code>，<code>authentication.principal()</code> 等于 <code>authentication.getPrincipal()</code>，<code>authentication.toString()</code> 等于 <code>"Authentication[token=token-1, principal=null, subject=null]"</code>。
     */
    @Test
    void beanAndComponentMethodsExposeTheSameValue() {
        BearerSubjectAuthenticator.Authentication authentication =
                new BearerSubjectAuthenticator.Authentication("token-1", null, null);
        BearerSubjectAuthenticator.Authentication equal =
                new BearerSubjectAuthenticator.Authentication("token-1", null, null);

        assertThat(authentication.token()).isEqualTo(authentication.getToken());
        assertThat(authentication.principal()).isEqualTo(authentication.getPrincipal());
        assertThat(authentication.subject()).isEqualTo(authentication.getSubject());
        assertThat(authentication).isEqualTo(equal);
        assertThat(authentication.hashCode()).isEqualTo(equal.hashCode());
        assertThat(authentication.toString()).isEqualTo(
                "Authentication[token=token-1, principal=null, subject=null]");
    }

    /**
     * 验证<code>restored</code> 等于 <code>authentication</code>。
     */
    @Test
    void jsonRoundTripRestoresTheAuthenticationValue() {
        BearerSubjectAuthenticator.Authentication authentication =
                new BearerSubjectAuthenticator.Authentication("token-1", null, null);

        String json = JsonKit.toJson(authentication);
        BearerSubjectAuthenticator.Authentication restored =
                JsonKit.toObject(json, BearerSubjectAuthenticator.Authentication.class);

        assertThat(restored).isEqualTo(authentication);
    }
}
