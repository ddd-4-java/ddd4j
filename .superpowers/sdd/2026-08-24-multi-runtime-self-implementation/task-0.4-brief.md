### Task 0.4：CI 验证 + commit 阶段 0 完成标记

- [ ] **Step 1: 跑全量 verify**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw verify -pl ddd4j-core,ddd4j-dependencies`

Expected: BUILD SUCCESS

- [ ] **Step 2: 验证 ArchUnit CoreIndependenceTest**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-core test -Dtest=CoreIndependenceTest`

Expected: Tests passed

- [ ] **Step 3: 全工程 grep 验证**

Run: `grep -rn "org\.fuin" /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j --include="*.java" --include="pom.xml"`

Expected: 仅匹配 README/docs 里的参考链接，源代码 0 匹配

- [ ] **Step 4: 推送**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git push origin feature/2.0.x
```

---

## 阶段 1：高精度参考文档 + ADR（5-7 天）

