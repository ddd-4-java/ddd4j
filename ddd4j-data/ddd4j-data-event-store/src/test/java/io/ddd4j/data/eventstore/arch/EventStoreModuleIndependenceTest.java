package io.ddd4j.data.eventstore.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-event-store 模块独立性自检：确保框架无关的 EventStore SPI
 * 不被运行时或持久化框架污染（ADR-0003／ADR-0005）。
 *
 * <p>本测试在 CI 阶段自动执行，防止以下耦合再次引入：
 * <ul>
 *   <li>Spring Framework（实现归 ddd4j-data-event-store-jpa 等下游模块）</li>
 *   <li>Jakarta Persistence（同上，SPI 层保持纯 Java + ddd4j-core）</li>
 * </ul>
 *
 * <p>并以允许清单式规则 {@link #module_deps_allowlist} 锁定总依赖面：
 * 仅 JDK、io.ddd4j 家族模块与 Jackson 三件套（Task 3.3 EventPayloadSerializer 用）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.eventstore", importOptions = {ImportOption.DoNotIncludeTests.class})
class EventStoreModuleIndependenceTest {

    /**
     * SPI 模块不得直接依赖 Spring Framework。
     */
    @ArchTest
    static final ArchRule no_spring_in_event_store =
            noClasses().that().resideInAPackage("io.ddd4j.data.eventstore..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * SPI 模块不得直接依赖 Jakarta Persistence（JPA 映射归阶段 4 的 jpa 实现模块）。
     */
    @ArchTest
    static final ArchRule no_jakarta_persistence_in_event_store =
            noClasses().that().resideInAPackage("io.ddd4j.data.eventstore..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

    /**
     * SPI 模块依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块、
     * Jackson（annotation／core／databind）与 Lombok 编译期标记。
     * 新增依赖必须显式加白并经 ADR 修订。
     */
    @ArchTest
    static final ArchRule module_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.eventstore..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "com.fasterxml.jackson.annotation..",
                            "com.fasterxml.jackson.core..",
                            "com.fasterxml.jackson.databind..",
                            "lombok.."
                    );
}
