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

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

import java.util.Objects;

/**
 * 基于 {@link InMemorySubject} 的内存版 {@link SubjectProvider}。
 *
 * <p>把所有 Subject 请求路由到同一个共享的 {@link InMemorySubject} 实例（适合单进程/单租户场景）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class InMemorySubjectProvider implements SubjectProvider {

    /** \u8fd4\u56de\u7ed9\u8c03\u7528\u65b9\u7684\u5185\u5b58\u8ba4\u8bc1\u4e3b\u4f53\u9002\u914d\u5668\u3002 */
    private final InMemorySubject subject;

    /**
     * \u521b\u5efa\u5171\u4eab\u5185\u5b58\u4e3b\u4f53\u63d0\u4f9b\u8005\u3002
     * @param subject \u5171\u4eab\u4e3b\u4f53\u5b9e\u4f8b
     */
    public InMemorySubjectProvider(InMemorySubject subject) {
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
    }

    /**
     * 获取值。
     * @return 方法执行结果。
     */
    @Override
    public Subject getSubject() {
        return subject;
    }

    /**
     * 获取值。
     * @param realm 字符串参数。
     * @return 方法执行结果。
     */
    @Override
    public Subject getSubject(String realm) {
        return subject;
    }
}
