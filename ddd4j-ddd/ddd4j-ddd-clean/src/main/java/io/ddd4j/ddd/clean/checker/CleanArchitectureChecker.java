package io.ddd4j.ddd.clean.checker;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.ddd.clean.rules.CleanDDDLayerRules;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Clean Architecture 目录规范检查器。
 * <p>
 * 检查项目是否符合 Clean Architecture 的分层约束：
 * <pre>
 * src/main/java/{base}/
 * ├── domain/              ← 领域层（Entities + Value Objects + Repository 接口）
 * │   ├── entity/
 * │   ├── valueobject/
 * │   └── repository/
 * ├── application/         ← 应用层（Use Cases + Application Services）
 * │   ├── service/
 * │   └── command/
 * ├── adapter/             ← 适配器层（Interface Adapters）
 * │   ├── persistence/     ← 持久化适配器（Repository 实现）
 * │   ├── web/             ← Web 适配器（Controller）
 * │   └── messaging/       ← 消息适配器
 * └── infrastructure/      ← 框架层（Frameworks & Drivers）
 *     ├── config/
 *     └── external/
 * </pre>
 * <p>
 * 依赖规则（由内向外）：
 * <ul>
 *   <li>domain → 不依赖任何外层</li>
 *   <li>application → 只依赖 domain</li>
 *   <li>adapter → 依赖 application 和 domain</li>
 *   <li>infrastructure → 依赖所有层</li>
 * </ul>
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 3.4.x
 */
@Slf4j
public class CleanArchitectureChecker {

    private static final Set<String> REQUIRED_LAYERS = Set.of(
            "domain", "application", "adapter", "infrastructure"
    );

    private static final Set<String> DOMAIN_SUB_PACKAGES = Set.of(
            "entity", "valueobject", "repository", "event", "service"
    );

    private static final Set<String> APPLICATION_SUB_PACKAGES = Set.of(
            "service", "command", "query", "port"
    );

    private static final Set<String> ADAPTER_SUB_PACKAGES = Set.of(
            "persistence", "web", "messaging", "gateway"
    );

    private final String basePackage;
    final List<String> violations = new ArrayList<>();

    public CleanArchitectureChecker(String basePackage) {
        this.basePackage = basePackage;
    }

    /**
     * 检查指定源码根目录是否符合 Clean Architecture 规范。
     *
     * @param sourceRoot 源码根目录（如 src/main/java 或 target/classes）
     * @return 违规列表，空表示合规
     */
    public List<String> check(String sourceRoot) {
        violations.clear();
        Path basePath = Paths.get(sourceRoot, basePackage.replace('.', File.separatorChar));

        if (!basePath.toFile().exists()) {
            violations.add("基础包路径不存在: " + basePath);
            return violations;
        }

        // 1. 目录结构检查（轻量级，无需 classpath）
        checkRequiredLayers(basePath);

        // 2. ArchUnit 类依赖检查（完整级，需要可被 import 的 class 文件）
        //    sourceRoot 可以是 src/main/java（Maven 编译前的源文件）
        //    也可以是 target/classes（已编译的 .class 文件）
        try {
            checkArchUnitRules(sourceRoot);
        } catch (Exception e) {
            log.warn("ArchUnit 检查失败（classpath 不可用？）：{}", e.getMessage());
        }

        return violations;
    }

    /**
     * 使用 ArchUnit 做类级别的依赖检查。
     */
    private void checkArchUnitRules(String sourceRoot) {
        // 尝试 importPath：优先用已编译的 class，否则用源文件目录
        JavaClasses classes = importClasses(sourceRoot);
        if (classes == null || classes.isEmpty()) {
            log.debug("ArchUnit: 未找到可分析的 class 文件，跳过依赖检查");
            return;
        }

        // 应用 CleanDDDLayerRules 中的核心规则
        checkArchRule(CleanDDDLayerRules.DOMAIN_NOT_DEPEND_ON_WEB, classes);
        checkArchRule(CleanDDDLayerRules.DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE, classes);
        checkArchRule(CleanDDDLayerRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORK, classes);
        checkArchRule(CleanDDDLayerRules.DOMAIN_ENTITY_IN_DOMAIN, classes);
        checkArchRule(CleanDDDLayerRules.DOMAIN_SERVICE_IN_DOMAIN, classes);
        checkArchRule(CleanDDDLayerRules.APPLICATION_SERVICE_IN_APP, classes);
        checkArchRule(CleanDDDLayerRules.REPOSITORY_IMPL_IN_INFRASTRUCTURE, classes);
    }

    private JavaClasses importClasses(String sourceRoot) {
        try {
            File root = new File(sourceRoot);
            // 优先尝试 target/classes（已编译 class 文件）
            File classRoot = new File(root, "../classes");
            File actualRoot = classRoot.exists() ? classRoot : root;

            return new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPath(actualRoot.toPath());
        } catch (Exception e) {
            log.debug("ArchUnit: importPath 失败 {}", e.getMessage());
            return null;
        }
    }

    private void checkArchRule(ArchRule rule, JavaClasses classes) {
        try {
            rule.check(classes);
        } catch (AssertionError e) {
            // 把 ArchUnit 的失败信息拆分为单行违规
            String message = e.getMessage();
            if (message != null) {
                for (String line : message.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("Architecture Violation")) {
                        violations.add(trimmed);
                    } else if (trimmed.startsWith("Architecture Violation")) {
                        violations.add(trimmed);
                    }
                }
            }
        }
    }

    private void checkRequiredLayers(Path basePath) {
        for (String layer : REQUIRED_LAYERS) {
            Path layerPath = basePath.resolve(layer);
            if (!layerPath.toFile().isDirectory()) {
                violations.add("缺少必要分层目录: " + layer + " (expected: " + layerPath + ")");
            }
        }
    }

    /**
     * 获取规范要求的目录结构描述。
     */
    public String getExpectedStructure() {
        return """
                Clean Architecture 目录规范：
                %s/
                ├── domain/              ← 领域层（零外部依赖）
                │   ├── entity/
                │   ├── valueobject/
                │   └── repository/
                ├── application/         ← 应用层（Use Cases）
                │   ├── service/
                │   └── command/
                ├── adapter/             ← 适配器层
                │   ├── persistence/
                │   ├── web/
                │   └── messaging/
                └── infrastructure/      ← 框架层
                    ├── config/
                    └── external/
                """.formatted(basePackage);
    }
}
