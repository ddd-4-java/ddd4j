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
package io.ddd4j.ddd.cola;

import io.ddd4j.ddd.cola.checker.ColaArchitectureChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 三线共同的 COLA 架构目录说明文本契约。
 */
class ColaArchitectureStructureContractTest {

    /** JUnit \u4e3a\u672c\u6b21\u6d4b\u8bd5\u5206\u914d\u7684\u4e34\u65f6\u76ee\u5f55\u3002 */
    @TempDir
    Path temporary;

    /** \u9a8c\u8bc1 COLA \u76ee\u5f55\u7ed3\u6784\u8bf4\u660e\u4e0e\u7ea6\u5b9a\u6587\u672c\u9010\u884c\u4e00\u81f4\uff0c\u5e76\u4e14\u672b\u5c3e\u4e0d\u542b\u6362\u884c\u3002 */
    @Test
    void expectedStructureHasExactTextWithoutTrailingLineBreak() {
        String description = new ColaArchitectureChecker("example").getExpectedStructure();

        assertEquals(String.join("\n",
                "COLA 架构目录规范：",
                "example/",
                "├── domain/                  ← 领域层（零外部依赖）",
                "│   ├── model/",
                "│   ├── ability/",
                "│   └── gateway/",
                "├── adapter/                 ← 适配器层（实现 gateway）",
                "│   ├── persistence/",
                "│   ├── web/",
                "│   └── messaging/",
                "├── application/             ← 应用层（编排领域能力）",
                "│   ├── executor/",
                "│   ├── query/",
                "│   └── extension/",
                "└── infrastructure/          ← 基础设施层",
                "    ├── config/",
                "    ├── external/",
                "    └── common/"), description);
    }

    /**
     * \u6784\u9020\u7f3a\u5c11\u5206\u5c42\u76ee\u5f55\u7684 COLA \u9879\u76ee\u5e76\u6838\u5bf9\u8fdd\u89c4\u9879\u987a\u5e8f\u3002
     * @throws Exception \u521b\u5efa\u6d4b\u8bd5\u76ee\u5f55\u6216\u8bfb\u53d6\u626b\u63cf\u7ed3\u679c\u5931\u8d25
     */
    @Test
    void missingLayerViolationsFollowDocumentedLayerOrder() throws Exception {
        Files.createDirectories(temporary.resolve("example"));
        List<String> missingLayers = new ColaArchitectureChecker("example").check(temporary.toString())
                .stream()
                .filter(violation -> violation.startsWith("缺少必要分层目录: "))
                .map(violation -> violation.substring("缺少必要分层目录: ".length()).split(" ")[0])
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("domain", "adapter", "application", "infrastructure"), missingLayers);
    }
}
