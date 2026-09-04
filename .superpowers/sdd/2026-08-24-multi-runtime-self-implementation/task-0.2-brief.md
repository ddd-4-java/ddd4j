### Task 0.2：删除 ProjectionService.java 注释中的 fuin 引用

**Files:**
- Modify: `ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/ProjectionService.java:5-6`

- [ ] **Step 1: 定位 fuin 引用**

Run: `grep -n "org.fuin" /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/ProjectionService.java`

Expected: 命中 `org.fuin.*` 引用

- [ ] **Step 2: 重写注释**

改写 javadoc：

```java
/**
 * 投影位置服务（纯 Java，零框架依赖）。
 *
 * <p>API 形态对齐 {@code cqrs-4-java} 的 ProjectionService 语义，但完全独立实现。
 * 框架适配层（如 {@code ddd4j-runtime-spring}）提供 JPA 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
```

- [ ] **Step 3: 验证**

Run: `grep -rn "org.fuin" /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/`

Expected: 0 个匹配

- [ ] **Step 4: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/ProjectionService.java
git commit -m "docs(core): ProjectionService 注释移除 fuin 引用"
```

---

