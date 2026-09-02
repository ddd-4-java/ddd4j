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
package io.ddd4j.core.subject;

/**
 * 匿名（未登录）{@link SubjectProvider}。
 *
 * <p>canonical 匿名实现，供以下场景直接使用，避免各业务/示例工程重复编写：
 * <ul>
 *   <li>单元测试 / 集成测试中不需要鉴权时；</li>
 *   <li>未接入 sa-token / shiro / spring-security 的最小运行环境；</li>
 *   <li>作为 {@link SubjectProvider} 的缺省兜底。</li>
 * </ul>
 *
 * <p>所有 {@link #getSubject()} 调用均返回 {@code null}（匿名：未登录），
 * 调用方需按"可能未登录"处理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AnonymousSubjectProvider implements SubjectProvider {

    /**
     * 共享单例（无状态，可全局复用）。
     */
    public static final AnonymousSubjectProvider INSTANCE = new AnonymousSubjectProvider();

    @Override
    public Subject getSubject() {
        return null; // 匿名：未登录
    }
}
