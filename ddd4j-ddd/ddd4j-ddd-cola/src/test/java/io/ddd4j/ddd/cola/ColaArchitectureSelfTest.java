package io.ddd4j.ddd.cola;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import io.ddd4j.ddd.cola.test.ColaArchitectureTest;

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

}
