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
package io.ddd4j.ddd.clean;

import io.ddd4j.ddd.clean.checker.CleanArchitectureChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 三线共同的架构目录说明文本契约。
 */
class CleanArchitectureStructureContractTest {

    /**
     * JUnit 为本次测试分配的临时目录。
     */
    @TempDir
    Path temporary;

    /**
     * 验证 Clean 架构预期结构的末尾直接结束于最后一个目录名。
     */
    @Test
    void expectedStructureEndsAtLastDirectoryWithoutTrailingLineBreak() {
        String description = new CleanArchitectureChecker("example").getExpectedStructure();

        assertEquals(String.join("\n",
                "Clean Architecture 目录规范：",
                "example/",
                "├── domain/              ← 领域层（零外部依赖）",
                "│   ├── entity/",
                "│   ├── valueobject/",
                "│   └── repository/",
                "├── application/         ← 应用层（Use Cases）",
                "│   ├── service/",
                "│   └── command/",
                "├── adapter/             ← 适配器层",
                "│   ├── persistence/",
                "│   ├── web/",
                "│   └── messaging/",
                "└── infrastructure/      ← 框架层",
                "    ├── config/",
                "    └── external/"), description);
    }

    /**
     * 构造缺少分层目录的 Clean 项目并核对违规项顺序。
     *
     * @throws Exception 创建测试目录或读取扫描结果失败
     */
    @Test
    void missingLayerViolationsFollowDocumentedLayerOrder() throws Exception {
        Files.createDirectories(temporary.resolve("example"));
        List<String> missingLayers = new CleanArchitectureChecker("example").check(temporary.toString())
                .stream()
                .filter(violation -> violation.startsWith("缺少必要分层目录: "))
                .map(violation -> violation.substring("缺少必要分层目录: ".length()).split(" ")[0])
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("domain", "application", "adapter", "infrastructure"), missingLayers);
    }
}
