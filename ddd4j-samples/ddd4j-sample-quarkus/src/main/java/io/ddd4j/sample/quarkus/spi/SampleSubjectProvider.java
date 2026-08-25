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

import java.util.Set;

/** Development Subject provider used to demonstrate the generic Bearer bridge. */
@Slf4j
@ApplicationScoped
public class SampleSubjectProvider implements SubjectProvider {

    private final InMemorySubject subject;
    private final String token;

    public SampleSubjectProvider() {
        subject = new InMemorySubject(event -> log.debug("Authentication event: {}", event));
        token = subject.login(AuthRequest.of("sample-user").setPrincipal(new AuthPrincipal()
                .setLoginId("sample-user")
                .setUserId("sample-user")
                .setPerms(Set.of("order:read", "order:write"))));
    }

    @Override
    public Subject getSubject() {
        return subject;
    }

    public String token() {
        return token;
    }
}
