package io.ddd4j.boot.ddd;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * ddd4j-ddd 架构约束测试。
 *
 * <p>验证本模块（纯净 DDD 轨道）的架构边界：
 * <ul>
 *   <li>不得依赖 MyBatis Plus（与 ddd4j-data 的 MP 轨道隔离）</li>
 *   <li>不得依赖 ddd4j-core 的 BaseEntity（AR 轨道）</li>
 *   <li>不得依赖 Servlet/Web 框架</li>
 *   <li>所有公开适配类必须在 {@code io.ddd4j.boot.ddd} 包下</li>
 * </ul>
 *
 * @author wandl
 * @since 3.4.x
 */
class DDDArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.ddd4j.boot.ddd");

    /**
     * 纯净 DDD 轨道不得依赖 MyBatis Plus。
     *
     * <p>这是本模块与 {@code ddd4j-data} 的根本边界。
     * 如果有人误把 MyBatis Plus 相关代码放进本模块，此测试会失败。
     */
    @Test
    void ddd_module_should_not_depend_on_mybatis_plus() {
        noClasses().that().resideInAPackage("..ddd4j.boot.ddd..")
                .should().dependOnClassesThat().resideInAPackage("com.baomidou..")
                .because("ddd4j-ddd 是纯净 DDD 轨道，不得依赖 MyBatis Plus（那是 ddd4j-data 的职责）")
                .check(classes);
    }

    /**
     * 纯净 DDD 轨道不得依赖 MyBatis 原生 API。
     */
    @Test
    void ddd_module_should_not_depend_on_mybatis_native() {
        noClasses().that().resideInAPackage("..ddd4j.boot.ddd..")
                .should().dependOnClassesThat().resideInAPackage("org.apache.ibatis..")
                .because("ddd4j-ddd 不得依赖 MyBatis 原生 API")
                .check(classes);
    }

    /**
     * 纯净 DDD 轨道不得依赖 Servlet API（领域层框架无关）。
     */
    @Test
    void ddd_module_should_not_depend_on_servlet() {
        noClasses().that().resideInAPackage("..ddd4j.boot.ddd..")
                .should().dependOnClassesThat().resideInAPackage("jakarta.servlet..")
                .because("ddd4j-ddd 领域层不得依赖 Servlet API")
                .check(classes);
    }

    /**
     * 纯净 DDD 轨道不得继承 ddd4j-core 的 BaseEntity（AR 轨道）。
     *
     * <p>BaseEntity 继承 MyBatis Plus 的 Model，与本模块的纯净定位冲突。
     * 两者可以共存于同一项目，但不能混用在同一个聚合根上。
     *
     * <p>注意：用全限定名字符串匹配，避免 ArchUnit 导入 BaseEntity 类时触发
     * MyBatis Plus 的 Model 类加载（本模块排除了 MP 依赖）。
     */
    @Test
    void ddd_module_should_not_extend_base_entity() {
        noClasses().that().resideInAPackage("..ddd4j.boot.ddd..")
                .should().haveFullyQualifiedName("io.ddd4j.boot.core.entity.BaseEntity")
                .because("ddd4j-ddd 的聚合根必须继承 DddAggregateRoot，不得使用 BaseEntity（AR 轨道）")
                .check(classes);
    }

    /**
     * 所有适配类应该在 io.ddd4j.boot.ddd 包下（命名规范）。
     */
    @Test
    void all_classes_should_be_in_ddd_package() {
        classes().that().resideInAPackage("..ddd4j.boot.ddd..")
                .should().resideInAnyPackage("..ddd4j.boot.ddd.aggregate..",
                        "..ddd4j.boot.ddd.event..",
                        "..ddd4j.boot.ddd.command..",
                        "..ddd4j.boot.ddd.repository..",
                        "..ddd4j.boot.ddd.config..")
                .because("ddd4j-ddd 的类应按职责分包：aggregate/event/command/repository/config")
                .check(classes);
    }

}
