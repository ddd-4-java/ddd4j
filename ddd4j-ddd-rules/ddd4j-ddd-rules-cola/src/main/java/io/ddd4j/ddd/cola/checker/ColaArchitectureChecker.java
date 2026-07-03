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
import java.util.Objects;
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
 * │   ├── web/                 ← Web 适配器
 * │   └── messaging/           ← 消息适配器
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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class ColaArchitectureChecker {

    /**
     * COLA 架构必需的四层目录名称
     */
    private static final Set<String> REQUIRED_LAYERS = Set.of(
            "domain", "adapter", "application", "infrastructure"
    );

    /**
     * 领域层（domain）推荐的子包名称
     */
    private static final Set<String> DOMAIN_SUB_PACKAGES = Set.of(
            "model", "ability", "gateway", "event"
    );

    /**
     * 应用层（application）推荐的子包名称
     */
    private static final Set<String> APPLICATION_SUB_PACKAGES = Set.of(
            "executor", "query", "extension", "service"
    );

    /**
     * 适配器层（adapter）推荐的子包名称
     */
    private static final Set<String> ADAPTER_SUB_PACKAGES = Set.of(
            "persistence", "web", "messaging", "rpc"
    );
    /**
     * 违规项列表
     */
    final List<String> violations = new ArrayList<>();
    /**
     * 业务根包名（如 com.example.myapp）
     */
    private final String basePackage;
    /**
     * 业务项目使用的 @DomainEntity 注解类
     */
    private final Class<? extends java.lang.annotation.Annotation> domainEntityAnnotation;
    /**
     * 业务项目使用的 @DomainService 注解类
     */
    private final Class<? extends java.lang.annotation.Annotation> domainServiceAnnotation;
    /**
     * 业务项目使用的 @ApplicationService 注解类
     */
    private final Class<? extends java.lang.annotation.Annotation> applicationServiceAnnotation;
    /**
     * 业务项目使用的 @DomainRepository 注解类
     */
    private final Class<? extends java.lang.annotation.Annotation> domainRepositoryAnnotation;

    /**
     * 构造器（向后兼容）：仅做目录结构检查，不应用注解驱动规则。
     */
    public ColaArchitectureChecker(String basePackage) {
        this(basePackage, null, null, null, null);
    }

    /**
     * 构造器（带注解驱动规则）。
     */
    public ColaArchitectureChecker(String basePackage,
                                   Class<? extends java.lang.annotation.Annotation> domainEntityAnnotation,
                                   Class<? extends java.lang.annotation.Annotation> domainServiceAnnotation,
                                   Class<? extends java.lang.annotation.Annotation> applicationServiceAnnotation,
                                   Class<? extends java.lang.annotation.Annotation> domainRepositoryAnnotation) {
        this.basePackage = basePackage;
        this.domainEntityAnnotation = domainEntityAnnotation;
        this.domainServiceAnnotation = domainServiceAnnotation;
        this.applicationServiceAnnotation = applicationServiceAnnotation;
        this.domainRepositoryAnnotation = domainRepositoryAnnotation;
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
        if (Objects.isNull(classes) || classes.isEmpty()) {
            log.debug("ArchUnit: 未找到可分析的 class 文件，跳过依赖检查");
            return;
        }

        // 应用 ColaDDDLayerRules 中的核心规则
        checkArchRule(ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_ADAPTER, classes);
        checkArchRule(ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_APPLICATION, classes);
        checkArchRule(ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORK, classes);
        if (Objects.nonNull(domainEntityAnnotation)) {
            checkArchRule(ColaDDDLayerRules.domainEntityInDomain(domainEntityAnnotation), classes);
        }
        if (Objects.nonNull(domainServiceAnnotation)) {
            checkArchRule(ColaDDDLayerRules.domainServiceInDomain(domainServiceAnnotation), classes);
        }
        if (Objects.nonNull(applicationServiceAnnotation)) {
            checkArchRule(ColaDDDLayerRules.applicationServiceInApp(applicationServiceAnnotation), classes);
        }
        if (Objects.nonNull(domainRepositoryAnnotation)) {
            checkArchRule(ColaDDDLayerRules.repositoryImplInAdapter(domainRepositoryAnnotation), classes);
        }
    }

    /**
     * 使用 ClassFileImporter 导入指定源码根目录的 class 文件。
     * <p>优先尝试导入已编译的 class 文件（target/classes），失败时回退到源文件目录。</p>
     *
     * @param sourceRoot 源码根目录
     * @return 导入的 Java 类集合，导入失败时返回 null
     */
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

    /**
     * 执行单条 ArchUnit 规则检查，并捕获违规信息。
     *
     * @param rule    ArchUnit 规则
     * @param classes 待检查的 Java 类集合
     */
    private void checkArchRule(ArchRule rule, JavaClasses classes) {
        try {
            rule.check(classes);
        } catch (AssertionError e) {
            String message = e.getMessage();
            if (Objects.nonNull(message)) {
                for (String line : message.split("\n")) {
                    String trimmed = line.trim();
                    if (!io.ddd4j.kit.lang.StrKit.isEmpty(trimmed)) {
                        violations.add(trimmed);
                    }
                }
            }
        }
    }

    /**
     * 检查基础包路径下是否包含 COLA 必需的四层目录。
     *
     * @param basePath 基础包路径
     */
    private void checkRequiredLayers(Path basePath) {
        for (String layer : REQUIRED_LAYERS) {
            Path layerPath = basePath.resolve(layer);
            if (!layerPath.toFile().isDirectory()) {
                violations.add("缺少必要分层目录: " + layer + " (expected: " + layerPath + ")");
            }
        }
    }

    /**
     * 检查 COLA 应用层（application）是否包含 executor/query/service 子包。
     * <p>COLA 特有：应用层应至少包含 executor（命令执行器）或 query（查询服务）子包之一。</p>
     *
     * @param basePath 基础包路径
     */
    private void checkApplicationLayerStructure(Path basePath) {
        Path appPath = basePath.resolve("application");
        if (!appPath.toFile().exists()) {
            return;
        }

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
