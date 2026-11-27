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

    /** \u8bfb\u53d6\u548c\u4fdd\u5b58\u7528\u6237\u7684\u4ed3\u50a8\u3002 */
    private final InMemoryUserRepository userRepository;

    /**
     * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>WorkingSubjectProvider</code> \u5b9e\u4f8b\u3002
     * @param userRepository \u8bfb\u53d6\u548c\u4fdd\u5b58\u7528\u6237\u7684\u4ed3\u50a8
     */
    public WorkingSubjectProvider(InMemoryUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 取得当前请求使用的认证主体接口。
     *
     * @return Subject 实例
     */
    @Override
    public Subject getSubject() {
        return new WorkingShiroSubject(userRepository);
    }

    /**
     * 取得当前请求使用的认证主体接口。
     *
     * @param realm 账号体系标识（如 sa-token 的 "admin"/"user"）
     * @return 对应 realm 的 Subject 实例
     */
    @Override
    public Subject getSubject(String realm) {
        return new WorkingShiroSubject(userRepository);
    }
}
