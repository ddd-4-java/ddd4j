# Task 3.1 Report：ddd4j-data-event-store 模块骨架

**Status: COMPLETE** | Commit `3e683c91` on `feature/2.0.x`（基线 `2ef42196`）

## 交付内容

- **新建** `ddd4j-data/ddd4j-data-event-store/pom.xml`（48 行，纯骨架，零 .java）：
  - 结构照抄 `ddd4j-data-crypto/pom.xml`：POM 4.0.0 XML 头 + xsi schemaLocation、parent `io.ddd4j:ddd4j-data`（`${revision}` + `relativePath ../pom.xml`）、`name`/`description`/`url` 齐备。
  - 依赖仅两块：`ddd4j-core`（`${revision}`）+ `jackson-databind`（走 BOM，无 version 标签）。无 Spring/JPA/任何运行时依赖，无 ArchUnit（无代码不配）。
  - description 说明框架无关 EventStore SPI + Jackson 序列化抽象，实现由 `ddd4j-data-event-store-{jpa,panache,jdbi,r2dbc}` 提供。
- **修改** `ddd4j-data/pom.xml`：`<modules>` 按字母序插入 `<module>ddd4j-data-event-store</module>`（datascope 之后、external 之前），单行 diff。

## Brief corrections（对计划 sketch 的修正记录）

1. **根 pom 不改**：计划 sketch Step 2 要求在根 `pom.xml` 注册孙模块——错误。根 pom 已聚合 `ddd4j-data`，孙模块只在 `ddd4j-data/pom.xml` 注册（核对现状：其余 7 个 data 子模块均如此）。本次仅改 1 个 pom 的 modules。
2. **提交信息**：按 controller 指定用「模块骨架」而非 sketch 的「SPI 模块骨架」。

## 门禁

`./mvnw -pl ddd4j-data/ddd4j-data-event-store -am install -DskipTests` → **BUILD SUCCESS**（reactor 7 模块全 SUCCESS：ddd4j / dependencies / annotation / kit / core / data / data-event-store）。

## 发现与处理：Maven 4 空模块 install 失败（唯一实质问题）

**现象**：首次门禁失败——`maven-install-plugin: The packaging plugin ... did not assign a main file to the project but it has attachments`。

**排查**（复现于 /tmp 最小工程，Maven 4.0.0-rc-6）：根 pom 全局 `maven-jar-plugin` 配 `skipIfEmpty=true`，无源码模块跳过 jar 后主文件为 null；而 Maven 4 core 对 jar 打包项目自身注册了内部 attachment，触发 install 插件「无主文件但有附件」守卫。排除 source-plugin/jacoco（各自 -D skip 后仍失败）；最小工程去掉 skipIfEmpty 即 SUCCESS，确认因果。

**处置**：仅在 `ddd4j-data-event-store/pom.xml` 本地覆盖 `<skipIfEmpty>false</skipIfEmpty>`（带中文注释说明），骨架期产出空 jar 通过门禁；Task 3.2 落地 SPI 源码后该覆盖即成无操作，与兄弟模块行为一致。未动根 pom 全局策略，未加 Java 文件，未改 packaging。

## Files changed

- `ddd4j-data/ddd4j-data-event-store/pom.xml`（新增）
- `ddd4j-data/pom.xml`（+1 行注册）

## Concerns / Notes for downstream

- Task 3.2 加源码后可（可选）移除 skipIfEmpty 覆盖，恢复完全继承根 pom 配置；不移除亦无害。
- 本模块空 jar 会安装进本地仓库（仅 manifest），3.2 起即为正常 jar。
