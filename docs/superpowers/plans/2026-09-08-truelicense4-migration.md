# TrueLicense 4.x Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development。使用现有checkout；用户禁止worktree，且本轮不提交/推送/发布。

**Goal:** 三线统一Apache2 TrueLicense4.1.4，处理公开API变更并证明许可证生成/验证及旧文件兼容。

**Architecture:** auth-license用4.x上下文+vendor/consumer组合替代1.x继承；沿用官方4.x V1格式桥接已有文件。扩展模块独立签名协议不变。

**Tech Stack:** JDK8/17/21, Maven3/3/4, TrueLicense4.1.4, JUnit, JKS/DSA, Preferences隔离。

**Spec:** ../specs/2026-09-08-truelicense4-migration.md

## Global Constraints

- 三线相同API和行为，现有模块树与revision不变。
- 版本集中管理，不新增数字模块版本、不保留旧1.x运行依赖、不靠许可证白名单刷绿。
- 不改Shiro/JPA/MQ，不建worktree/切分支/提交/推送/发布，保留用户脏改。
- 新Java代码使用import Objects判空、SLF4J；不打印密码、私钥或完整参数对象。
- 只读取测试生成合成密钥；Preferences隔离到test target目录或独立节点并严格清理其自有节点。

### Task 1: 三线引擎与公开API迁移

**Files:** 三线同路径：
- Modify: ddd4j-dependencies/pom.xml（仅TrueLicense条目）
- Modify: ddd4j-auth/ddd4j-auth-license/pom.xml
- Modify: ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/CustomKeyStoreParam.java
- Modify: ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/LicenseVerify.java
- Modify: ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/manager/CustomLicenseManager.java
- Modify: ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/creator/LicenseCreator.java
- Modify: ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/creator/LicenseCreatorParam.java（仅敏感toString防护/必要兼容）
- Modify: ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/LicenseProperties.java（显式signatureAlgorithm配置）
- Modify: ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/DefaultLicenseAutoConfiguration.java（透传signatureAlgorithm）
- Create/Test: ddd4j-auth/ddd4j-auth-license/src/test/java/io/ddd4j/auth/license/TrueLicenseMigrationTest.java
- Create/Test: 同目录 LicenseTestSupport.java
- Create/Test: 同目录 LicenseTestPreferencesFactory.java（仅测试内存PreferencesFactory，Surefire启动指定；BeforeAll确认实际根节点属于该factory，覆盖高层默认Preferences调用）
- Create/Test resource: src/test/resources/truelicense-legacy/ 下仅合成证书/公钥及生成来源说明，不提交生产凭据。
- Create: docs/migrations/truelicense4.md（主线，API变更表与运行证据）

**Interfaces:** 按规格低层API；保留高层五参数LicenseVerify和LicenseCreator参数签名。先对当前已编译类做javap基线，再对新class比对；旧LicenseParam构造和LicenseManager继承不可伪称二进制兼容。

- [x] Step 1: 添加新API编译与真实生命周期测试，运行得到RED（旧构造/4.x License类型不可用）。例如：
```java
CustomLicenseManager manager = new CustomLicenseManager(subject, keyStoreParam, isolatedPreferences);
manager.store(content, licenseFile);
assertEquals(subject, verifier.install(licenseFile).getSubject());
assertEquals(subject, verifier.verify().getSubject());
verifier.uninstall();
assertThrows(LicenseManagementException.class, verifier::verify);
```
content必须由4.x V1.builder().subject(subject).build().licenseFactory()创建，真实keytool合成DSA/JKS公私钥，不mock签名。
- [x] Step 2: BOM以truelicense.version=4.1.4集中管理global.namespace.truelicense:truelicense-v1（及显式需要的api/core）。移除旧core/xml管理项，模块引用不写数字版本。
- [x] Step 3: 最小组合实现。V1.builder().subject(subject)创建上下文；authentication使用CustomKeyStoreParam磁盘流和明确password protection。4.x install后必须verify再load；失败则清理安装状态。保留subject/date/consumer约束，禁止自行XMLDecoder绕过官方验签。
- [x] Step 4: 增加篡改、错公钥/subject、过期/未生效、失败安装、卸载与旧1.33合成文件兼容用例。旧夹具可用既有1.33 JAR在/tmp独立生成，最终Maven依赖与测试runtime必须无1.x；记录来源与命令。不要复制上游旧实现。

保留已生成的有效历史夹具：1024bitDSA公钥证书自签SHA256withDSA，许可证由1.33以SHA1withDSA签发。默认五参数入口显式SHA1withDSA应消费成功；新六参数入口及CreatorParam配置SHA256withDSA用于2048bit新签发，错算法必须拒绝；null/空白显式算法拒绝。不通过重生成仅适合4.x默认推断的夹具掩盖兼容失败。
- [x] Step 5: 三线对应JDK运行 -B -ntp -Pparity-verification -pl ddd4j-auth/ddd4j-auth-license -am -Dtest=TrueLicenseMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test；不跳过Enforcer，保留RED/GREEN。
- [x] Step 6: 写API迁移表（保留/改变/删除继承成员）、V1格式与算法边界、旧证书证明和三线命令；自审后由主控独立审查，不提交。

### Task 2: 依赖许可门禁与最终验证

**Files:** 主线docs/migrations/truelicense4.md、计划状态；必要的三线config/license-selections.tsv仅对新坐标有权威许可证据时更新，不改历史选择。

**Interfaces:** 消费Task1的新API、测试、坐标；交付三线fresh dependency tree、SBOM/license gate、API对比。

- [ ] Step 1: 三线运行auth-license默认test（无-Dtest）和extension-license既有测试，记录真实数/失败/skips。
- [ ] Step 2: 用现有仓库许可证脚本/配置执行涉及模块的许可检查与SBOM，证明无de.schlichtherle.truelicense 1.33运行依赖，保留上游Apache2来源。
- [ ] Step 3: 新鲜字节码API对比及源码结构检查，逐项解释本次三线同步公开API变更；编译高层旧调用示例和新低层调用示例。
- [ ] Step 4: 最终独立审查、三线git diff --check，更新证据，不宣称与本次无关的Shiro全量失败已解决或整体生产就绪。
