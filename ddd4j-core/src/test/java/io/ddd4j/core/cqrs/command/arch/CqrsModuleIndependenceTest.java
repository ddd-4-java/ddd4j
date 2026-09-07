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

    @ArchTest
    static final ArchRule no_spring_in_cqrs_module =
            noClasses().that().resideInAPackage("io.ddd4j.core.cqrs.command..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule no_quarkus_in_cqrs_module =
            noClasses().that().resideInAPackage("io.ddd4j.core.cqrs.command..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");

    @ArchTest
    static final ArchRule cqrs_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.core.cqrs.command..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "lombok.."
                    );
}
