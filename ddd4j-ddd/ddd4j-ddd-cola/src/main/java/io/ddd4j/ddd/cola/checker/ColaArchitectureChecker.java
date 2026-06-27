package io.ddd4j.ddd.cola.checker;

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
 * @author wandl
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
        checkAdapterImplementsGateway(basePath);
        checkApplicationLayerStructure(basePath);

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

        // COLA 核心规则：domain 层不依赖 adapter/application/infrastructure
        checkNoExternalImports(domainPath, Set.of(
                basePackage + ".adapter",
                basePackage + ".application",
                basePackage + ".infrastructure",
                "org.springframework",
                "com.baomidou"
        ), "domain");
    }

    private void checkAdapterImplementsGateway(Path basePath) {
        Path gatewayPath = basePath.resolve("domain").resolve("gateway");
        Path adapterPath = basePath.resolve("adapter");
        if (!gatewayPath.toFile().exists() || !adapterPath.toFile().exists()) return;

        // 检查 adapter 是否实现了 gateway 接口（骨架级检查）
        log.debug("COLA: Checking adapter implements gateway interfaces");
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

    private void checkNoExternalImports(Path layerPath, Set<String> forbiddenPackages, String layerName) {
        // 骨架级检查：仅验证目录结构，不扫描源码
        // 生产环境建议集成 ArchUnit 做完整的类依赖分析
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
