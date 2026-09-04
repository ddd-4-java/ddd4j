### Task 0.1：删除 ddd4j-dependencies/pom.xml 中 fuin 死依赖块

**Files:**
- Modify: `ddd4j/ddd4j-dependencies/pom.xml:274-275`（删除 2 个 version 属性）
- Modify: `ddd4j/ddd4j-dependencies/pom.xml:3620-3675`（删除 8 个 dependency 块）

**Interfaces:**
- 消费：无
- 产出：干净的 `ddd4j-dependencies/pom.xml` BOM

- [ ] **Step 1: 删除 fuin 版本属性**

Read `ddd4j/ddd4j-dependencies/pom.xml:274-275`，确认内容为：

```xml
        <fuin-ddd4j.version>0.7.0</fuin-ddd4j.version>
        <fuin-cqrs4j.version>0.6.0</fuin-cqrs4j.version>
```

用 Edit 工具删除这两行。

- [ ] **Step 2: 删除 8 个 fuin dependency 块**

Read `ddd4j/ddd4j-dependencies/pom.xml:3620-3675`，确认内容包含 8 个 fuin 依赖块：
- `org.fuin.ddd4j:ddd-4-java-core / esc / jsonb / jackson / jaxb`（5 个）
- `org.fuin.cqrs4j:cqrs-4-java-core / jsonb / jackson`（3 个）

用 Edit 工具逐个删除 8 个 dependency 块（含 Source URL 注释和中文描述注释）。

- [ ] **Step 3: 验证编译**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-dependencies install -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 4: 验证 ddd4j-core 全模块编译**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-core compile`

Expected: BUILD SUCCESS（无 fuin 引用，零影响）

- [ ] **Step 5: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add ddd4j-dependencies/pom.xml
git commit -m "chore(deps): 删除 ddd4j-dependencies BOM 中 8 个 fuin 死依赖"
```

---

