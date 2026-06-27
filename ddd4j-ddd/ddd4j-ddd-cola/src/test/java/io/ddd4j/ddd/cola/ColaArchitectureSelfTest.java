package io.ddd4j.ddd.cola;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.ddd.cola.test.ColaArchitectureTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ddd4j-ddd-cola 模块自身的架构自检。
 *
 * <p>扫描本模块（{@code io.ddd4j.ddd.cola}）的代码，验证自身代码也满足 COLA 规则。
 * 继承 {@link ColaArchitectureTest} 复用所有规则（注解驱动 + 包依赖）。
 */
@AnalyzeClasses(
        packages = "io.ddd4j.ddd.cola",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ColaArchitectureSelfTest extends ColaArchitectureTest {

    /**
     * 规则：Checker 类的 violation 列表不能暴露为 public。
     * <p>违反封装性：业务项目可以直接修改 violations，绕过检查。
     */
    @ArchTest
    public static final ArchRule checker_violation_must_be_package_private = classes()
            .that().haveSimpleNameEndingWith("Checker")
            .and().resideInAPackage("..cola.checker..")
            .should().haveOnlyPrivateFields()
            .because("Checker 类的 violations 字段必须是 package-private，外部不能直接修改");

    /**
     * 规则：测试基类必须是 abstract（业务项目不能直接实例化）。
     * <p>排除 SelfTest 自身（继承基类后会变成 concrete class 用于 JUnit 运行）。
     */
    @ArchTest
    public static final ArchRule test_base_must_be_abstract = classes()
            .that().haveSimpleNameEndingWith("ArchitectureTest")
            .and().resideInAPackage("..cola.test..")
            .and().doNotHaveSimpleName("ColaArchitectureSelfTest")
            .should().beAbstract()
            .because("架构测试基类必须 abstract，业务项目应通过继承使用");
}
