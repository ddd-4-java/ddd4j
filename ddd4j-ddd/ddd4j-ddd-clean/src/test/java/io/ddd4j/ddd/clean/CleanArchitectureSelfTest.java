package io.ddd4j.ddd.clean;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.ddd.clean.test.CleanArchitectureTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ddd4j-ddd-clean 模块自身的架构自检。
 *
 * <p>扫描本模块（{@code io.ddd4j.ddd.clean}）的代码，验证自身代码也满足 Clean Architecture 规则。
 * 继承 {@link CleanArchitectureTest} 复用所有规则（注解驱动 + 包依赖），但本类额外做几个
 * ddd4j-ddd 模块特定的合理性检查（因为 ddd4j-ddd 自身不在 domain/application/adapter 业务层级中）。
 *
 * <p>运行 {@code mvn test} 时，如果 ddd4j-ddd-clean 自身的代码违反规则，构建会失败。
 */
@AnalyzeClasses(
        packages = "io.ddd4j.ddd.clean",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class CleanArchitectureSelfTest extends CleanArchitectureTest {

    /**
     * 规则：Checker 类的 violation 列表不能暴露为 public。
     * <p>违反封装性：业务项目可以直接修改 violations，绕过检查。
     */
    @ArchTest
    public static final ArchRule checker_violation_must_be_package_private = classes()
            .that().haveSimpleNameEndingWith("Checker")
            .and().resideInAPackage("..clean.checker..")
            .should().haveOnlyPrivateFields()
            .because("Checker 类的 violations 字段必须是 package-private，外部不能直接修改");

    /**
     * 规则：测试基类必须是 abstract（业务项目不能直接实例化）。
     * <p>排除 SelfTest 自身（继承基类后会变成 concrete class 用于 JUnit 运行）。
     */
    @ArchTest
    public static final ArchRule test_base_must_be_abstract = classes()
            .that().haveSimpleNameEndingWith("ArchitectureTest")
            .and().resideInAPackage("..clean.test..")
            .and().doNotHaveSimpleName("CleanArchitectureSelfTest")
            .should().beAbstract()
            .because("架构测试基类必须 abstract，业务项目应通过继承使用");
}
