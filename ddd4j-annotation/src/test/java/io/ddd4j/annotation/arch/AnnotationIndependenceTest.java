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
package io.ddd4j.annotation.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-annotation 模块独立性自检：确保注解层零框架依赖。
 *
 * <p>本测试在 CI 阶段自动执行，防止以下耦合引入：
 * <ul>
 *   <li>Spring Framework</li>
 *   <li>MyBatis-Plus</li>
 *   <li>Jakarta Servlet</li>
 *   <li>任何 DI/IoC 框架</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.annotation", importOptions = {ImportOption.DoNotIncludeTests.class})
class AnnotationIndependenceTest {

    @ArchTest
    static final ArchRule no_spring =
            noClasses().that().resideInAPackage("io.ddd4j.annotation..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule no_mybatis =
            noClasses().that().resideInAPackage("io.ddd4j.annotation..")
                    .should().dependOnClassesThat().resideInAPackage("com.baomidou..");

    @ArchTest
    static final ArchRule no_servlet =
            noClasses().that().resideInAPackage("io.ddd4j.annotation..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.servlet..");

    @ArchTest
    static final ArchRule no_cdi =
            noClasses().that().resideInAPackage("io.ddd4j.annotation..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "javax.enterprise..",
                            "jakarta.enterprise.."
                    );

    @ArchTest
    static final ArchRule no_guice =
            noClasses().that().resideInAPackage("io.ddd4j.annotation..")
                    .should().dependOnClassesThat().resideInAPackage("com.google.inject..");
}
