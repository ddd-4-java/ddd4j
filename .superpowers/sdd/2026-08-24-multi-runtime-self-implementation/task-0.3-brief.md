### Task 0.3：删除 docs 中 fuin 引用

**Files:**
- Modify: `ddd4j/docs/ddd/1、DDD 经典分层架构目录结构.md`
- Modify: `ddd4j/README.md`（如有 fuin 引用）

- [ ] **Step 1: 定位文档引用**

Run: `grep -rn "fuin\|org\.fuin" /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/docs/ /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/README.md`

Expected: 列出所有文档引用

- [ ] **Step 2: 改写为自研表述**

对每处 `fuin` 引用，改写为「自研 / ddd4j-core 抽象」。若有 fuin 仓库 URL 作为外部参考链接，**保留**，但加 `（参考来源，不依赖）`）标记。

- [ ] **Step 3: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/ README.md
git commit -m "docs: 删除 fuin 依赖表述，标注为外部参考链接"
```

---

