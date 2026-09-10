# License Gate Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以精确 Maven 版本坐标、SPDX选择和固定官方证据修复 ddd4j 1.0.x、3.0.x 许可证门禁，并保持2.0.x继续通过。

**Architecture:** 保留 `verify-license-policy.sh` 作为CI稳定入口，将报告解析、证据校验和策略判断迁移到可单元测试的 Python实现。三线共享相同脚本接口和TSV schema，但每条线只记录其真实依赖版本；无法证明兼容的依赖必须升级、替换或移除。

**Tech Stack:** Bash、Python 3标准库、Maven License Plugin 2.5.0、CycloneDX SBOM、GitHub Actions、JDK 8/17/21。

**Spec:** `docs/superpowers/specs/2026-09-10-license-gate-hardening-design.md`

## Global Constraints

- 自动允许宽松许可证；EPL/MPL/CDDL仅以未修改独立JAR形式有条件允许；GPL/AGPL/LGPL及非商业、商业专有、未知许可证默认阻断。
- 选择记录必须精确到 `groupId:artifactId:version`。
- 官方证据必须使用固定版本POM、LICENSE、tag或commit URL。
- 禁止 group/artifact级宽泛白名单、正则坐标、空理由和漂移分支URL。
- 三线校验器对象、函数、参数、shell入口一致；依赖版本和选择记录允许不同。
- 不使用 `-Denforcer.skip`、跳过许可证job或吞掉解析错误制造假绿。
- 阶段1全部通过前不修改MQ、Readiness、认证、EventStore和Outbox。

---

### Task 1: 建立可测试的许可证策略引擎

**Files:**
- Create: `scripts/license_policy.py`
- Create: `scripts/test_license_policy.py`
- Modify: `scripts/verify-license-policy.sh`

**Interfaces:**
- Consumes: `parse_inventory(path: Path) -> list[InventoryEntry]`、`load_selections(path: Path) -> dict[str, LicenseSelection]`。
- Produces: `verify_policy(inventory, selections, sbom, build_tool_exclusions) -> list[str]`。

- [ ] **Step 1: 写解析和策略失败测试**

覆盖完整版本坐标、宽松许可证、EPL/MPL/CDDL条件通过、多许可证选择、缺失证据、漂移URL、版本不匹配、陈旧条目、强copyleft、未知许可证和空报告。

- [ ] **Step 2: 验证红灯**

Run: `python3 scripts/test_license_policy.py`

Expected: FAIL，因为 `license_policy.py` 尚不存在。

- [ ] **Step 3: 实现最小策略引擎**

使用 `dataclasses`、`csv`、`re`、`urllib.parse` 和 `pathlib`；不增加第三方Python依赖。官方证据允许 `github.com` 固定tag/commit路径及 Maven Central/上游官方域名，拒绝 `/blob/main/`、`/master/`。

- [ ] **Step 4: 保持shell入口兼容**

`verify-license-policy.sh` 只负责确保报告存在并调用：

```bash
python3 scripts/license_policy.py \
  --inventory "${LICENSE_FILE}" \
  --selections "${SELECTIONS_FILE}" \
  --sbom "${SBOM_FILE}" \
  --build-tool-exclusions "${BUILD_TOOL_EXCLUSIONS_FILE}"
```

- [ ] **Step 5: 验证绿灯**

Run: `python3 scripts/test_license_policy.py`

Expected: PASS。

### Task 2: 升级许可证选择证据schema

**Files:**
- Modify: `config/license-selections.tsv`
- Modify: `scripts/test_license_policy.py`

**Interfaces:**
- Consumes: 旧两列选择表。
- Produces: `coordinate, declared_expression, selected_spdx, evidence_url, evidence_type, justification` 六列TSV。

- [ ] **Step 1: 写schema失败测试**

要求所有字段非空、坐标含版本、SPDX属于允许集合、证据类型只允许 `POM/LICENSE/UPSTREAM_SOURCE`；MulanPSL选择必须提供OSI、SPDX或上游固定版本证据。

- [ ] **Step 2: 验证旧TSV失败**

Run: `python3 scripts/test_license_policy.py`

Expected: FAIL，指出旧两列schema。

- [ ] **Step 3: 迁移当前已有选择**

逐条从对应版本的上游POM或LICENSE补充固定证据；不得把现有正则许可证文本直接当SPDX。

- [ ] **Step 4: 拒绝陈旧选择**

真实报告中未出现的选择记录必须失败，防止依赖升级后遗留永久豁免。

- [ ] **Step 5: 验证schema与策略测试通过**

Run: `python3 scripts/test_license_policy.py`

Expected: PASS。

### Task 3: 修复1.0.x许可证门禁

**Files:**
- Modify: `config/license-selections.tsv`

**Interfaces:**
- Consumes: JDK8真实 `THIRD-PARTY.txt` 和 CycloneDX SBOM。
- Produces: 1.0.x精确许可证证据集。

- [ ] **Step 1: 在独立1.0.x checkout生成真实报告**

```bash
JAVA_HOME=/Users/wandl/Library/Java/JavaVirtualMachines/corretto-1.8.0_504/Contents/Home \
  ./scripts/generate-sbom.sh
JAVA_HOME=/Users/wandl/Library/Java/JavaVirtualMachines/corretto-1.8.0_504/Contents/Home \
  ./scripts/generate-license-report.sh
```

- [ ] **Step 2: 记录当前失败基线**

Run: `./scripts/verify-license-policy.sh`

Expected: FAIL，至少覆盖实际报告中的 JSQLParser 4.9、Javax Activation/JAXB/EL/Jersey、JNA和JCIP问题；以真实输出为准，不添加报告外坐标。

- [ ] **Step 3: 逐坐标核实官方证据**

优先读取本地Maven POM的 `<licenses>` 和 `<scm>`，再访问对应固定tag/commit的官方LICENSE。双许可证明确选择 Apache/EPL/CDDL；发现无兼容分支时停止本任务，记录精确坐标并为依赖升级或替换单独提交设计审批，禁止在许可证选择表中放行。

- [ ] **Step 4: 验证许可证门禁**

Run: `./scripts/verify-license-policy.sh`

Expected: PASS，且 violations文件为空。

- [ ] **Step 5: 完整JDK8回归**

Run: `JAVA_HOME=/Users/wandl/Library/Java/JavaVirtualMachines/corretto-1.8.0_504/Contents/Home ./mvnw clean verify`

Expected: 103/103模块成功，零测试失败/错误/跳过。

### Task 4: 保持2.0.x许可证门禁稳定

**Files:**
- Modify: `scripts/license_policy.py`
- Modify: `scripts/test_license_policy.py`
- Modify: `scripts/verify-license-policy.sh`
- Modify: `config/license-selections.tsv`

**Interfaces:**
- Consumes: 与1.0.x相同schema和函数签名。
- Produces: JDK17验证结果，不引入1.0.x/3.0.x不存在的版本记录。

- [ ] **Step 1: 同步校验器和测试**
- [ ] **Step 2: 迁移2.0.x实际选择记录并删除陈旧条目**
- [ ] **Step 3: 生成真实SBOM和THIRD-PARTY报告**
- [ ] **Step 4: 执行许可证门禁**

Run: `JAVA_HOME=/Users/wandl/Library/Java/JavaVirtualMachines/corretto-17.0.20.1/Contents/Home ./scripts/verify-license-policy.sh`

Expected: PASS。

- [ ] **Step 5: 完整JDK17回归**

Run: `JAVA_HOME=/Users/wandl/Library/Java/JavaVirtualMachines/corretto-17.0.20.1/Contents/Home ./mvnw clean verify`

Expected: 121/121模块成功，零测试失败/错误/跳过。

### Task 5: 修复3.0.x JNA许可证门禁

**Files:**
- Modify: `scripts/license_policy.py`
- Modify: `scripts/test_license_policy.py`
- Modify: `scripts/verify-license-policy.sh`
- Modify: `config/license-selections.tsv`

**Interfaces:**
- Consumes: `net.java.dev.jna:jna:5.18.1` 的真实报告表达式和固定官方证据。
- Produces: JDK21许可证通过证据。

- [ ] **Step 1: 生成真实3.0.x报告并复现JNA失败**
- [ ] **Step 2: 从JNA 5.18.1官方POM/LICENSE确认多许可证表达式**
- [ ] **Step 3: 写入完整版本选择、SPDX、证据类型和理由**
- [ ] **Step 4: 执行许可证及Maven4模型门禁**

```bash
./scripts/verify-license-policy.sh
python3 scripts/test_maven4_model_contract.py
```

Expected: 全部PASS。

- [ ] **Step 5: 完整JDK21/Maven4回归**

Run: `JAVA_HOME=/Users/wandl/Library/Java/JavaVirtualMachines/ms-21.0.12.1/Contents/Home ./mvnw clean verify`

Expected: 121/121模块成功，零测试失败/错误/跳过，Resolver file-lock为0。

### Task 6: CI与三线一致性验收

**Files:**
- Verify: `.github/workflows/verify.yml`
- Verify: `.github/workflows/release-candidate.yml`

**Interfaces:**
- Consumes: 三线许可证脚本、TSV和真实报告。
- Produces: 三线可审计CI证据。

- [ ] **Step 1: 对比三线校验器API和shell入口**

要求 `license_policy.py`、测试和 `verify-license-policy.sh` 结构一致；选择记录只按实际版本不同。

- [ ] **Step 2: 验证CI不跳过许可证错误**

工作流必须直接执行 `generate-license-report.sh` 和 `verify-license-policy.sh`，不使用 `continue-on-error`。

- [ ] **Step 3: 提交并双推三线**

提交信息：`fix(release): enforce evidenced license selections`。

- [ ] **Step 4: 等待GitHub Actions**

要求三线 `SBOM and license reports` job成功；保存run URL、commit SHA和job结论。

- [ ] **Step 5: 阶段完成判定**

仅当1.0.x、2.0.x、3.0.x本地许可证门禁、完整Reactor和GitHub Actions全部通过，阶段1才完成并允许进入MQ启动与生命周期设计。
