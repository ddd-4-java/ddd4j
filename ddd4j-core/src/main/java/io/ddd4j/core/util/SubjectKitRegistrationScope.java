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

import io.ddd4j.core.subject.SubjectProvider;

import java.util.Objects;

/**
 * SubjectKit 静态门面的可恢复注册作用域。
 *
 * <p>运行时适配器既会向 Context SPI 注册 SubjectProvider，也需要为仍在使用
 * {@link SubjectKit} 的业务代码维护同一个 Provider。本作用域在关闭时只恢复自己
 * 持有的注册，避免覆盖应用后续替换的 Provider。
 */
public final class SubjectKitRegistrationScope implements AutoCloseable {

    private final SubjectProvider subjectProvider;
    private SubjectProvider previousSubjectProvider;
    private boolean started;

    public SubjectKitRegistrationScope(SubjectProvider subjectProvider) {
        this.subjectProvider = Objects.requireNonNull(subjectProvider, "subjectProvider must not be null");
    }

    /**
     * 注册当前运行时提供的 SubjectProvider。
     */
    public synchronized void start() {
        if (started) {
            return;
        }
        previousSubjectProvider = SubjectKit.subjectProvider;
        SubjectKit.register(subjectProvider);
        started = true;
    }

    /**
     * 恢复运行时启动前的 SubjectProvider。
     */
    @Override
    public synchronized void close() {
        if (!started) {
            return;
        }
        if (Objects.equals(SubjectKit.subjectProvider, subjectProvider)) {
            SubjectKit.register(previousSubjectProvider);
        }
        previousSubjectProvider = null;
        started = false;
    }
}
