package io.ddd4j.ddd.clean;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import io.ddd4j.ddd.clean.test.CleanArchitectureTest;

/**
 * ddd4j-ddd-clean 模块自身的架构自检。
 *
 * <p>扫描本模块（{@code io.ddd4j.ddd.clean}）的代码，验证自身代码也满足 Clean Architecture 规则。
 * 继承 {@link CleanArchitectureTest} 复用所有规则（注解驱动 + 包依赖）。
 *
 * <p>运行 {@code mvn test} 时，如果 ddd4j-ddd-clean 自身的代码违反规则，构建会失败。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@AnalyzeClasses(
        packages = "io.ddd4j.ddd.clean",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class CleanArchitectureSelfTest extends CleanArchitectureTest {

}
