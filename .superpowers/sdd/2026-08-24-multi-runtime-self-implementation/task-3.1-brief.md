### Task 3.1：建 ddd4j-data-event-store 模块骨架

**Files:**
- Create: `ddd4j/ddd4j-data/ddd4j-data-event-store/pom.xml`
- Modify: `ddd4j/ddd4j-data/pom.xml`
- Modify: `ddd4j/pom.xml`

- [ ] **Step 1: 在 ddd4j-data/pom.xml 加新模块**

Read `ddd4j/ddd4j-data/pom.xml`，找到 `<modules>` 段，添加 `<module>ddd4j-data-event-store</module>`。

- [ ] **Step 2: 在 ddd4j/pom.xml 加新子模块**

Read `ddd4j/pom.xml`，找到 `<modules>` 段，添加 `<module>ddd4j-data/ddd4j-data-event-store</module>`。

- [ ] **Step 3: 创建 ddd4j-data-event-store/pom.xml**

Write `ddd4j-data-event-store/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-data</artifactId>
    <version>${revision}</version>
  </parent>

  <artifactId>ddd4j-data-event-store</artifactId>
  <name>${project.groupId}:${project.artifactId}</name>
  <description>ddd4j 事件存储模块：EventStore SPI + Jackson 序列化抽象。
   不绑定任何运行时或持久化框架，由 ddd4j-data-event-store-{jpa,panache,jdbi,r2dbc}  提供实现。</description>

  <dependencies>
    <dependency>
      <groupId>io.ddd4j</groupId>
      <artifactId>ddd4j-core</artifactId>
      <version>${revision}</version>
    </dependency>

    <!-- Jackson 序列化 -->
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-data/ddd4j-data-event-store install -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add ddd4j-data/ddd4j-data-event-store/pom.xml ddd4j-data/pom.xml pom.xml
git commit -m "feat(data): 建 ddd4j-data-event-store SPI 模块骨架"
```

---


---

## Controller context（修正计划 sketch 两处）

1. **注册位置修正**：本模块是 `ddd4j-data` 的子模块——**只在 `ddd4j-data/pom.xml` 的 `<modules>` 注册**（按字母序插在 `ddd4j-data-datascope` 之后、`ddd4j-data-external` 之前，即 `<module>ddd4j-data-event-store</module>`）。计划 sketch 说还要改根 `pom.xml`——**错误**，根 pom 已含 `ddd4j-data` 聚合器，孙模块不重复注册（现状核对：7 个既有 data 子模块均只注册于 ddd4j-data/pom.xml）。报告里记为 brief correction。
2. **pom 模板**：parent = `io.ddd4j:ddd4j-data`（relativePath ../pom.xml，照抄 ddd4j-data-crypto/pom.xml 的骨架结构含 XML 头/modelVersion 4.0.0/url 声明）。依赖仅两块：`ddd4j-core`（${revision}）+ `jackson-databind`（版本走 BOM，无 version 标签）。description 说明：框架无关 EventStore SPI + Jackson 序列化抽象，实现由 ddd4j-data-event-store-{jpa,panache,jdbi,r2dbc} 提供。**不依赖 Spring/JPA/任何运行时**。
3. 本任务**纯骨架**：pom + 注册 + 空目录，**不写任何 .java**（SPI 是 Task 3.2）也不加 ArchUnit（有代码才加）。
4. 门禁：`./mvnw -pl ddd4j-data/ddd4j-data-event-store -am install -DskipTests` BUILD SUCCESS（-am 必须，新 revision 兄弟快照未装本地）。
5. 单 commit：`feat(data): 建 ddd4j-data-event-store 模块骨架`。Edit 工具改 pom（铁律）。

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-3.1-report.md`。Reply ≤15 lines.
