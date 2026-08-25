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
package io.ddd4j.sample.javalin.spi;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * 认证主体提供者：返回空（匿名）Subject 的示例实现。
 *
 * <p>真实应用应注入基于 sa-token / shiro / spring-security 的 SubjectProvider。
 * 本示例只演示 SPI 注册流程，不参与鉴权逻辑。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class AnonymousSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return null; // 匿名：未登录
    }
}