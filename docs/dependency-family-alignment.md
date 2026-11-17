# 2.0.x 依赖家族一致性

2.0.x 固定使用 JDK 17、Spring Framework 6 / Security 6 / Boot 3 / Data JPA 3。
版本值由 `ddd4j-dependencies/pom.xml` 定义，子模块只引用共享属性。

## 检查方式

先使用 JDK 17 构建，再运行门禁；私服凭据沿用已有 Maven settings，不写入仓库。

```sh
./mvnw -B -ntp -DskipTests install
python3 scripts/verify_dependency_family_alignment.py --output target/dependency-families --check-bytecode
./mvnw -B -ntp verify
```

自定义 settings 可重复传递 `--maven-arg`，例如 `--maven-arg=-s --maven-arg=/path/settings.xml`。
自定义 Maven 本地仓库时，同时设置 Maven 的 `-Dmaven.repo.local` 和检查器的 `--repository`。

检查器复用三线 Spring 检查的项目枚举、有效模型导出和 JDK 基线校验，批量导出默认 reactor 的全部项目。
随后导出全部项目的实际依赖树，检查 test/provided 等默认解析依赖与原生 classifier。
缺少模型、依赖树、制品或检查期间 POM 变化时失败，不复用旧输出。
字节码校验复用现有 JAR 检查，排除 module-info 和 multi-release JAR 的版本目录；基础类不允许超过 Java 17。

## 版本归属与平台边界

- 默认 Netty 4.2；Quarkus、Helidon 3、Micronaut 4 / Panache 适配使用 Netty 4.1。
  一个模块实际依赖必须采用同一条线的统一补丁，tcnative 独立校验其 2.x 版本。
- Quarkus、Micronaut 的 Vert.x 使用中央定义的 4.x 兼容线，独立 Vert.x 适配使用 5.x。
  docgen 等独立发布组件按 BOM 的实际坐标版本比较，不能把整个 groupId 强制设成同一版本。
- 平台模块重新导入 BOM 时，Netty 兼容 BOM 必须优先于 Vert.x / Artemis / 平台 BOM。
  共享 ASM、Jackson、Reactor BOM 同样优先于会覆盖它们的平台 BOM。
- Netty 4.2 专有组件和 Vert.x 4 测试夹具的 classifier 在父 POM 显式管理，防止子平台重新引入旧补丁。
  兼容模块的管理目录可包含尚未使用的另一条线坐标，实际类路径仍禁止混装。
- Artemis 2.50+ 的真实声明使用 `org.apache.artemis`。官方 BOM 保留的旧组名 relocation 管理项
  仅允许相同补丁版本，不能把旧版本客户端当作兼容别名。
- Dropwizard 5.0.2 依赖 Logback 1.5 中的接口，保留兼容的 1.5.38；不能仅因 JDK 17 可加载
  Logback 1.6 就认定二进制兼容。

`.github/workflows/dependency-family-alignment.yml` 在 2.0.x push、PR 和手动触发时执行构建、门禁与完整回归。
原三线 Spring 一致性工作流继续保留。周更策略固定本次验证过的平台补丁，后续调整须同时通过这些门禁。

一致性通过不代表漏洞清零。漏洞扫描继续由既有 weekly-dependencies 工作流执行；公开补丁限制和
遗留风险见 [2.0.x 公开依赖遗留漏洞](security/2.0.x-public-dependency-residuals.md)。
