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

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import io.ddd4j.ddd.clean.test.CleanArchitectureTest;

/**
 * ddd4j-ddd-rules-clean 模块自身的架构自检。
 *
 * <p>扫描本模块（{@code io.ddd4j.ddd.clean}）的代码，验证自身代码也满足 Clean Architecture 规则。
 * 继承 {@link CleanArchitectureTest} 复用所有规则（注解驱动 + 包依赖）。
 *
 * <p>运行 {@code mvn test} 时，如果 ddd4j-ddd-rules-clean 自身的代码违反规则，构建会失败。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@AnalyzeClasses(
        packages = "io.ddd4j.ddd.clean",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class CleanArchitectureSelfTest extends CleanArchitectureTest {

}
