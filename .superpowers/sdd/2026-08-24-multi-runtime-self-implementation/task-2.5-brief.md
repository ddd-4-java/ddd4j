### Task 2.5：强化 ArchUnit CoreIndependenceTest

**Files:**
- Modify: `ddd4j/ddd4j-core/src/test/java/io/ddd4j/core/arch/CoreIndependenceTest.java`

- [ ] **Step 1: 增加 8 运行时架构守护**

Read `CoreIndependenceTest.java`，追加规则：

```java
@Test
public void noFuInReference() {
    noClasses()
        .that().resideInAPackage("io.ddd4j.core..")
        .should().dependOnClassesThat().resideInAPackage("org.fuin..");
}

@Test
public void coreHasZeroExternalDependencies() {
    // 校验 pom.xml 中只允许：jackson-databind, jackson-annotations, commons-lang3, transmittable-thread-local
    ClassesToClassesWrapper deps = classes()
        .that().resideInAPackage("io.ddd4j.core..");
    deps.should().onlyAccessClassesThat()
        .resideInAnyPackage("io.ddd4j.core..",
                            "java..",
                            "jakarta..",
                            "javax..",
                            "com.fasterxml.jackson.annotation..",
                            "com.fasterxml.jackson.databind..",
                            "com.fasterxml.jackson.core..",
                            "org.apache.commons.lang3..",
                            "com.alibaba..transmittable..");
}

@Test
public void noSpringDependencyInCore() {
    noClasses()
        .that().resideInAPackage("io.ddd4j.core..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework..");
}

@Test
public void noQuarkusDependencyInCore() {
    noClasses()
        .that().resideInAPackage("io.ddd4j.core..")
        .should().dependOnClassesThat().resideInAPackage("io.quarkus..");
}

@Test
public void noMicronautDependencyInCore() {
    noClasses()
        .that().resideInAPackage("io.ddd4j.core..")
        .should().dependOnClassesThat().resideInAPackage("io.micronaut..");
}
```

- [ ] **Step 2: 验证**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-core test -Dtest=CoreIndependenceTest`

Expected: Tests passed

- [ ] **Step 3: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add ddd4j-core/src/test/java/io/ddd4j/core/arch/CoreIndependenceTest.java
git commit -m "test(core): ArchUnit 强化 8 运行时零依赖守护"
```

---


---

## Controller context（事实清单已核实，按此执行；与计划 sketch 冲突处以本节为准）

### A. SLF4J 迁移义务①（ADR-0002 前置）——core 主源码字节码级清零 org.slf4j

已核实 6 处引用：
1. `constant/Constants.java:9-10,53,57,61` — 3 个 Marker 常量**全仓 0 使用**。改为 `public static final String ACCESS_MARKER = "io.hiwepy.access"`（AUTHZ_MARKER/BIZ_MARKER 同理），javadoc 注明 Marker→String 属 2.0.x 破坏性变更（ADR-0002 迁移义务①），删 2 个 org.slf4j import。
2. `ddd/event/DomainEvent.java:12,47` — @Slf4j 但 `log.` **零使用**（死注解）：删注解+import。
3. `cqrs/query/Query.java:14,67` — 同上死注解：删。
4. `context/ThreadContext.java:7,20` — log **有真实使用**（:73-74/:97-98/:115 等多段 trace 日志，纯诊断）。按 ADR-0002「core 内不直接依赖日志门面，日志由家族模块与适配层承担」：**删除整段 if(log.isTraceEnabled()) 块**（连同日志调用），保留块外业务逻辑原样；删注解+import。
5. `ddd/model/metadata/DomainModelHelper.java:3,44` — :95-96 一段 debug 日志：同上整块删除；删注解+import。
（kit 的 6 处 @Slf4j **不在本任务范围**。）

### B. ArchUnit 强化（CoreIndependenceTest，遵循现存 @ArchTest 字段风格——计划 sketch 的 @Test 方法风格作废）

新增 4 条规则（第 5 条 noSpring 与现存 `no_spring_in_core`(:37-40) 重复——**不新增**，在现存规则 javadoc 补一句跨引说明即可）：
- `no_fuin_reference`：io.ddd4j.core.. 不依赖 org.fuin..
- `core_zero_external_dependencies`：io.ddd4j.core.. onlyDependOnClassesThat resideInAnyPackage：`io.ddd4j.., java.., jakarta.., javax.., com.fasterxml.jackson.annotation.., com.fasterxml.jackson.databind.., com.fasterxml.jackson.core.., org.apache.commons.lang3.., com.alibaba.ttl.., io.swagger.v3..`（TTL 真实包名 com.alibaba.ttl 已核实 ThreadContext:3；swagger 豁免与 ADR-0002 一致——ApiRestResponse @Schema）。javadoc 注明白名单出处 ADR-0002 终态。
- `no_quarkus_in_core`、`no_micronaut_in_core`（照 no_spring 句式）
先读现存文件确认 @AnalyzeClasses 扫描范围与规则写法，**完全照其模式**新增。

### C. 门禁
- `./mvnw -pl ddd4j-core -am test` 全绿（基线 252；ArchUnit 新增 4 条规则计入测试数）
- `grep -rn "org.slf4j" ddd4j-core/src/main/java/` = 0 命中（test 作用域不限制）
- 单 commit：`feat(core): ArchUnit 零依赖守护强化 + SLF4J 字节码清零（ADR-0002 迁移义务①）`

### D. 明确不做（记 ledger deferred）
- BOM slf4j-api 作用域调整（义务②）——字节码规则已绿，classpath 卫生属跨模块变更，另行任务
- 过渡期 org.slf4j.. 允许清单——①落地后无需过渡项

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-2.5-report.md`（6 处引用逐处处置、规则清单与现存去重说明、门禁输出、self-review）。Reply ≤15 lines.
