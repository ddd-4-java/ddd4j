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
package io.ddd4j.extension.monitor.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码版本信息（从 {@code git.properties} 读取）。was {@code CodeVersionVO}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeVersion {
    /**
     * Git 分支名称
     */
    private String branch;
    /**
     * 构建时间
     */
    private String buildTime;
    /**
     * 构建版本号
     */
    private String buildVersion;
    /**
     * Git 提交 ID
     */
    private String commitId;
    /**
     * Git 提交信息
     */
    private String commitMessage;
    /**
     * Git 提交用户
     */
    private String commitUser;
    /**
     * Git 提交时间
     */
    private String commitTime;
}
