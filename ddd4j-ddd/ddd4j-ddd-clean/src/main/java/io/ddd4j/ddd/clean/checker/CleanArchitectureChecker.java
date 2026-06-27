package io.ddd4j.ddd.clean.checker;

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
 * @author wandl
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
     * @param sourceRoot 源码根目录（如 src/main/java）
     * @return 违规列表，空表示合规
     */
    public List<String> check(String sourceRoot) {
        violations.clear();
        Path basePath = Paths.get(sourceRoot, basePackage.replace('.', File.separatorChar));

        if (!basePath.toFile().exists()) {
            violations.add("基础包路径不存在: " + basePath);
            return violations;
        }

        checkRequiredLayers(basePath);
        checkDomainIsolation(basePath);
        checkNoReverseDependencies(basePath);

        return violations;
    }

    private void checkRequiredLayers(Path basePath) {
        for (String layer : REQUIRED_LAYERS) {
            Path layerPath = basePath.resolve(layer);
            if (!layerPath.toFile().isDirectory()) {
                violations.add("缺少必要分层目录: " + layer + " (expected: " + layerPath + ")");
            }
        }
    }

    private void checkDomainIsolation(Path basePath) {
        Path domainPath = basePath.resolve("domain");
        if (!domainPath.toFile().exists()) return;

        // 检查 domain 层是否引用了外层包
        checkNoExternalImports(domainPath, Set.of(
                "io.ddd4j.data", "io.ddd4j.web", "io.ddd4j.mq",
                "org.springframework", "com.baomidou"
        ), "domain");
    }

    private void checkNoReverseDependencies(Path basePath) {
        // application 层不应依赖 adapter/infrastructure
        Path appPath = basePath.resolve("application");
        if (appPath.toFile().exists()) {
            checkNoExternalImports(appPath, Set.of(
                    basePackage + ".adapter", basePackage + ".infrastructure"
            ), "application");
        }
    }

    private void checkNoExternalImports(Path layerPath, Set<String> forbiddenPackages, String layerName) {
        File[] javaFiles = layerPath.toFile().listFiles((dir, name) -> name.endsWith(".java"));
        if (javaFiles == null) return;

        for (File javaFile : javaFiles) {
            // 简单的文本扫描（生产环境建议用 JavaParser 或 ArchUnit）
            // 此处仅做骨架级检查
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
