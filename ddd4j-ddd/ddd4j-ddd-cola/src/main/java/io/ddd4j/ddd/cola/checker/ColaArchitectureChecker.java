package io.ddd4j.ddd.cola.checker;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.ddd.cola.rules.ColaDDDLayerRules;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * COLA 架构目录规范检查器。
 * <p>
 * 检查项目是否符合 COLA（Clean Object-oriented and Layered Architecture）的分层约束：
 * <pre>
 * src/main/java/{base}/
 * ├── domain/                  ← 领域层
 * │   ├── model/               ← 领域模型（Entity / Value Object / Aggregate）
 * │   ├── ability/             ← 领域能力（Domain Service）
 * │   └── gateway/             ← 领域网关（Repository / 外部服务接口）
 * ├── adapter/                 ← 适配器层
 * │   ├── persistence/         ← 持久化适配器
 │   ├── web/                 ← Web 适配器
 │   └── messaging/           ← 消息适配器
 * ├── application/             ← 应用层
 * │   ├── executor/            ← 命令执行器（Command Executor）
 * │   ├── query/               ← 查询服务（Query Service）
 * │   └── extension/           ← 扩展点（Extension Point）
 * └── infrastructure/          ← 基础设施层
 *     ├── config/              ← 配置
 *     ├── external/            ← 外部服务调用
 *     └── common/              ← 通用工具
 * </pre>
 * <p>
 * COLA 核心原则：
 * <ul>
 *   <li>领域层（domain）是核心，不依赖任何外层</li>
 *   <li>适配器层（adapter）实现领域网关（gateway）接口</li>
 *   <li>应用层（application）编排领域能力，不包含业务逻辑</li>
 *   <li>命令-查询分离（CQS）：executor 处理命令，query 处理查询</li>
 * </ul>
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 3.4.x
 */
@Slf4j
public class ColaArchitectureChecker {

    private static final Set<String> REQUIRED_LAYERS = Set.of(
            "domain", "adapter", "application", "infrastructure"
    );

    private static final Set<String> DOMAIN_SUB_PACKAGES = Set.of(
            "model", "ability", "gateway", "event"
    );

    private static final Set<String> APPLICATION_SUB_PACKAGES = Set.of(
            "executor", "query", "extension", "service"
    );

    private static final Set<String> ADAPTER_SUB_PACKAGES = Set.of(
            "persistence", "web", "messaging", "rpc"
    );

    private final String basePackage;
    final List<String> violations = new ArrayList<>();

    public ColaArchitectureChecker(String basePackage) {
        this.basePackage = basePackage;
    }

    /**
     * 检查指定源码根目录是否符合 COLA 规范。
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
        checkApplicationLayerStructure(basePath);

        // 2. ArchUnit 类依赖检查（完整级，需要可被 import 的 class 文件）
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
        JavaClasses classes = importClasses(sourceRoot);
        if (classes == null || classes.isEmpty()) {
            log.debug("ArchUnit: 未找到可分析的 class 文件，跳过依赖检查");
            return;
        }

        // 应用 ColaDDDLayerRules 中的核心规则
        checkArchRule(ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_ADAPTER, classes);
        checkArchRule(ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_APPLICATION, classes);
        checkArchRule(ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORK, classes);
        checkArchRule(ColaDDDLayerRules.DOMAIN_ENTITY_IN_DOMAIN, classes);
        checkArchRule(ColaDDDLayerRules.DOMAIN_SERVICE_IN_DOMAIN, classes);
        checkArchRule(ColaDDDLayerRules.APPLICATION_SERVICE_IN_APP, classes);
        checkArchRule(ColaDDDLayerRules.REPOSITORY_IMPL_IN_ADAPTER, classes);
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
            String message = e.getMessage();
            if (message != null) {
                for (String line : message.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
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

    private void checkApplicationLayerStructure(Path basePath) {
        Path appPath = basePath.resolve("application");
        if (!appPath.toFile().exists()) return;

        // COLA 特有：application 层应包含 executor 或 query 子包
        boolean hasExecutor = appPath.resolve("executor").toFile().isDirectory();
        boolean hasQuery = appPath.resolve("query").toFile().isDirectory();
        boolean hasService = appPath.resolve("service").toFile().isDirectory();

        if (!hasExecutor && !hasQuery && !hasService) {
            violations.add("COLA: application 层应包含 executor/ 或 query/ 或 service/ 子包");
        }
    }

    /**
     * 获取 COLA 规范要求的目录结构描述。
     */
    public String getExpectedStructure() {
        return """
                COLA 架构目录规范：
                %s/
                ├── domain/                  ← 领域层（零外部依赖）
                │   ├── model/
                │   ├── ability/
                │   └── gateway/
                ├── adapter/                 ← 适配器层（实现 gateway）
                │   ├── persistence/
                │   ├── web/
                │   └── messaging/
                ├── application/             ← 应用层（编排领域能力）
                │   ├── executor/
                │   ├── query/
                │   └── extension/
                └── infrastructure/          ← 基础设施层
                    ├── config/
                    ├── external/
                    └── common/
                """.formatted(basePackage);
    }
}
