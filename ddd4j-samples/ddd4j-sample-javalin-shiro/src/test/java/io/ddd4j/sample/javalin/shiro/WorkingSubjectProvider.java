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
package io.ddd4j.sample.javalin.shiro;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;

/**
 * 测试专用 SubjectProvider：返回 {@link WorkingShiroSubject}，以绕开上游 ShiroSubject.login bug。
 */
public class WorkingSubjectProvider implements SubjectProvider {

    private final InMemoryUserRepository userRepository;

    public WorkingSubjectProvider(InMemoryUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Subject getSubject() {
        return new WorkingShiroSubject(userRepository);
    }

    @Override
    public Subject getSubject(String realm) {
        return new WorkingShiroSubject(userRepository);
    }
}