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
package io.ddd4j.auth.shiro.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * Shiro Subject 工厂（纯 Java，零 Spring 依赖）。
 *
 * <p>注意：本实现的 {@link #getSubject()} 直接 new {@link ShiroSubject}，
 * <b>不</b>调用 {@link io.ddd4j.core.util.SubjectKit#getSubject()}（避免死循环）。
 * 这与 {@code SubjectProvider} 接口的 default 实现不同，必须在子类重写。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ShiroSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new ShiroSubject();
    }

    @Override
    public Subject getSubject(String realm) {
        // Shiro 多 Realm 场景由 SecurityManager 配置，此处返回默认 Subject
        return new ShiroSubject();
    }

}
