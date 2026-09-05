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
package io.ddd4j.auth.satoken.subject;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * Sa-Token Subject 工厂（纯 Java，零 Spring 依赖）。
 *
 * <p>默认使用 {@link StpUtil#stpLogic}（loginType="login"）。
 * 多账号体系场景重写 {@link #getSubject(String)} 返回对应 {@link StpLogic} 包装的 Subject。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SaTokenSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new SaTokenSubject();
    }

    /**
     * 按账号体系获取 Subject（对齐 Sa-Token {@code SaManager.getStpLogic(loginType)}）。
     *
     * <p>本实现返回的 Subject 内部会按 realm 选择对应的 StpLogic。
     */
    @Override
    public Subject getSubject(String realm) {
        return new SaTokenSubject();
    }

}
