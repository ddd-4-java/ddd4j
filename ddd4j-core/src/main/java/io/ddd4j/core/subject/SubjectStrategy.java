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

import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 核心行为策略集（对齐 Sa-Token 的 {@code SaStrategy}）。
 *
 * <p>所有核心行为做成 {@link Function} 字段，业务可热替换：
 * <pre>
 * SubjectKit.getStrategy().hasElement = (list, perm) -> list.stream().anyMatch(perm::matches);
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class SubjectStrategy {

    /**
     * 全局单例（对齐 SaStrategy.instance）
     */
    public static final SubjectStrategy instance = new SubjectStrategy();

    /**
     * Token 生成策略。
     * <p>输入登录请求，输出会话凭证字符串。
     */
    public Function<AuthRequest, String> createToken = AuthRequest::getLoginIdAsString;

    /**
     * 权限匹配策略（对齐 SaStrategy.hasElement）。
     * <p>输入权限列表和待校验权限，输出是否匹配。
     */
    public BiFunction<List<String>, String, Boolean> hasElement = List::contains;

    /**
     * 会话超时校验策略。
     * <p>输入 Token，输出是否已过期。
     */
    public Function<String, Boolean> isExpired = token -> false;

    /**
     * 多账号体系 Subject 创建策略（对齐 SaCreateStpLogicFunction）。
     * <p>输入账号体系标识（realm），输出对应 Subject 实例。
     */
    public Function<String, Subject> createSubject = realm -> SubjectKit.getSubject();

}
