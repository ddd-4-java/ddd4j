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
package io.ddd4j.sample.quarkus.spi;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;


/**
 * Development Subject provider used to demonstrate the generic Bearer bridge.
 */
@Slf4j
@ApplicationScoped
public class SampleSubjectProvider implements SubjectProvider {

    /** \u5f53\u524d\u8fd0\u884c\u8ba4\u8bc1\u903b\u8f91\u4f7f\u7528\u7684\u539f\u751f\u4e3b\u4f53\u3002 */
    private final InMemorySubject subject;
    /** \u5f53\u524d\u793a\u4f8b\u7b7e\u53d1\u6216\u8fd4\u56de\u5ba2\u6237\u7aef\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002 */
    private final String token;

    /**
     * 创建演示用主体并完成登录。
     */
    public SampleSubjectProvider() {
        subject = new InMemorySubject(event -> log.debug("Authentication event: {}", event));
        token = subject.login(AuthRequest.of("sample-user").setPrincipal(new AuthPrincipal()
                .setLoginId("sample-user")
                .setUserId("sample-user")
                .setPerms(new java.util.HashSet<>(java.util.Arrays.asList("order:read", "order:write")))));
    }

    /**
     * 取得当前请求使用的认证主体接口。
     *
     * @return 本次调用使用的认证主体接口
     */
    @Override
    public Subject getSubject() {
        return subject;
    }

    /** @return \u6f14\u793a\u4e3b\u4f53\u7684\u767b\u5f55\u4ee4\u724c */
    public String token() {
        return token;
    }
}
