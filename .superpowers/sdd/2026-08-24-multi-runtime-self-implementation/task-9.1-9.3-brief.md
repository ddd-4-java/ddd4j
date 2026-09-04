# Task 9.1-9.3 Brief — Stage 9 收尾：license + 全工程 verify + push（最终发布）

## 背景
- 阶段 0-8 全部完成（55 任务，全部 review approved）
- 当前 HEAD: `57cb8409`（feature/3.0.x，已推送 Aliyun）
- 当前分支: `feature/3.0.x`
- `ddd4j-core` 有 `CoreIndependenceTest`（6 ArchUnit rules），所有新模块也有各自的 ArchUnit 测试
- 全部新模块使用 `io.ddd4j` groupId，Apache-2.0 许可证（ddd4j 根 pom 声明）

## 交付

### A. Task 9.1: license-maven-plugin 验证全 Apache-2.0
1. 确认根 pom 已有 `license-maven-plugin` 配置（如果缺失则添加）
2. 执行 `./mvnw license:check` 验证所有 .java 文件有 Apache-2.0 header
3. 如果有缺失 header 的文件，执行 `./mvnw license:format` 补充
4. 单 commit（如有修改）

### B. Task 9.2: 全工程 grep 验证零 fuin 引用
1. `grep -rn "org\.fuin" /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j --include="*.java" --include="*.xml"` — 期望 0 匹配（仅 README/docs 里的参考链接允许）
2. `grep -rn "fuin" /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j --include="*.java"` — 期望 0 匹配
3. 如有残留，逐个修复（参考链接加 `（参考来源，不依赖）`）

### C. Task 9.3: 全工程 verify + push（最终发布）
1. `./mvnw verify -pl ddd4j-core -am -DskipTests` — 确认 BUILD SUCCESS
2. `./mvnw verify -pl ddd4j-core -am` — 确认 261 测试全绿
3. `git push origin feature/3.0.x` — 推送到 Aliyun
4. 如果有 stage 9 的新 commit，推送到 Aliyun

## 门禁
- license:check 全绿（Apache-2.0）
- grep fuin = 0（*.java/*.xml）
- verify BUILD SUCCESS + 261 测试全绿
- push 成功

## 提交
如需修改，单 commit：`chore(license): 补充缺失 Apache-2.0 header + 验证全工程零 fuin 引用`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-9.1-9.3-report.md`。Reply ≤15 lines.
