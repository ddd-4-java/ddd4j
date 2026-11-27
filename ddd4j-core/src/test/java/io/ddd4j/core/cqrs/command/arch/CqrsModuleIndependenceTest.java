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
package io.ddd4j.core.cqrs.command.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-core 中 CQRS 命令侧包的独立性自检。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.core.cqrs.command", importOptions = {ImportOption.DoNotIncludeTests.class})
class CqrsModuleIndependenceTest {

    /** SPI \u6a21\u5757\u4e0d\u5f97\u76f4\u63a5\u4f9d\u8d56 Spring Framework\u3002 */
    @ArchTest
    static final ArchRule no_spring_in_cqrs_module =
            noClasses().that().resideInAPackage("io.ddd4j.core.cqrs.command..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /** SPI \u6a21\u5757\u4e0d\u5f97\u76f4\u63a5\u4f9d\u8d56 Quarkus\u3002 */
    @ArchTest
    static final ArchRule no_quarkus_in_cqrs_module =
            noClasses().that().resideInAPackage("io.ddd4j.core.cqrs.command..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");

    /**
     * SPI \u6a21\u5757\u4f9d\u8d56\u5141\u8bb8\u6e05\u5355\uff1a\u4ec5 JDK\uff08java..\uff09\u4e0e io.ddd4j \u5bb6\u65cf\u6a21\u5757
     *  \uff08Command\uff0fCommandExecutor \u5951\u7ea6\u6765\u81ea ddd4j-core\uff09\u3002
     *  \u65b0\u589e\u4f9d\u8d56\u5fc5\u987b\u663e\u5f0f\u52a0\u767d\u5e76\u7ecf ADR \u4fee\u8ba2\u3002
     */
    @ArchTest
    static final ArchRule cqrs_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.core.cqrs.command..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "lombok.."
                    );
}
