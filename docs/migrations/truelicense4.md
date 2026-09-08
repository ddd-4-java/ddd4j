# TrueLicense 4.x 迁移指南

> 2026-09-08 回归复核：后续依赖对齐曾把旧 TrueLicense 1.33 坐标重新加入三条
> `ddd4j-dependencies` 和组件矩阵。本轮已删除这些回退项。三条线重新执行目标迁移测试，均为
> 20/20 通过；`ddd4j-auth-license` 与独立的 `ddd4j-extension-license` 联合回归均为 27/27 通过。
> Fresh runtime dependency tree 仅包含 `global.namespace.truelicense:*:4.1.4`。三线结构/API
> 校验显示 auth-license 无任何差异；校验整体仍被 1.0.x Micronaut Web 新增内部类这一无关红项阻塞，
> 不得据此宣称全仓三线完全一致。

## 迁移范围

`ddd4j-auth-license` 的三条维护线已从旧坐标
`de.schlichtherle.truelicense:truelicense-core:1.33` 迁移到
`global.namespace.truelicense:truelicense-v1:4.1.4`。版本仅由各线
`ddd4j-dependencies` 集中管理，业务模块不写数字版本。

TrueLicense 4.x 运行库使用 Apache License 2.0。这里选择 `truelicense-v1`
是为了继续读写 TrueLicense 1.x 文件格式，不代表密码算法现代化：V1 格式已被上游弃用，
仍使用 JKS 和 `PBEWithMD5AndDES`。新系统应规划迁移到更现代的格式和算法，而不是继续扩展 V1。

## 签名算法选择

库版本、许可证文件格式和签名算法是三个独立维度：

- 库版本：TrueLicense 4.1.4，Apache-2.0。
- 文件格式：`V1.builder()`，兼容 1.x 文件格式但已弃用。
- 签名算法：由 `signatureAlgorithm` 显式配置。

旧五参数 `CustomKeyStoreParam` 和 `LicenseVerify` 构造器，以及
`LicenseCreatorParam`/`LicenseProperties` 的默认值，均保持 `SHA1withDSA`，用于读取既有
1.33 许可证。需要新签发 2048-bit DSA 许可证时，必须通过六参数构造器或配置属性显式指定
`SHA256withDSA`，生成端和消费端必须一致。

外部消费方必须同时删除直接声明的旧 `de.schlichtherle.truelicense:*:1.33` 依赖。4.1.4 的
`truelicense-v1` 已自带官方 `de.schlichtherle.license.LicenseContent` 和
`de.schlichtherle.xml.GenericCertificate` 桥接类；新旧 JAR 并存会带来类遮蔽和类型不兼容风险。
外部框架选择新签名算法时，还必须把 `signatureAlgorithm` 透传到六参数入口。当前 Task 1
只完成本仓库三条 ddd4j 线，不能据此声称所有外部框架已经迁移。

## 公开 API 迁移表

| 旧 API | 4.x 迁移结果 | 兼容性与替代方式 |
|---|---|---|
| `LicenseVerify(String,String,String,String,String)` | 保留 | 源码/二进制签名保留；默认 `SHA1withDSA`。 |
| `LicenseVerify(...,String signatureAlgorithm)` | 新增 | 新许可证显式选择签名算法。 |
| `installLicense()` / `unInstallLicense()` / `verify()` | 保留 | 高层返回/参数不变；安装内部现在执行 install → verify → load，失败会清理状态。 |
| `LicenseCreator(LicenseCreatorParam)` / `generateLicense()` | 保留 | 高层签名不变；内部改用 4.x `License` 和 `VendorLicenseManager`。 |
| `LicenseCreatorParam` 原有 Bean 属性 | 保留 | Bean 属性及源码/二进制调用签名保持；这不是任意历史 Java 序列化 DTO 数据兼容证明。新增 `signatureAlgorithm`；显式 `toString()` 不输出 `storePass`/`keyPass`。 |
| `CustomKeyStoreParam(Class,String,String,String,String)` | 保留 | 不再继承 GPL 时代的 `AbstractKeyStoreParam`，因此类层次存在源码/二进制断点；默认 `SHA1withDSA`。 |
| `CustomKeyStoreParam(...,String signatureAlgorithm)` | 新增 | 用于显式算法配置；`getAlias/getStorePwd/getKeyPwd/getStream` 保留。 |
| 继承的 `AbstractKeyStoreParam.equals/hashCode` | 删除 | 新类使用普通对象身份语义；不复制旧父类实现。依赖旧值相等语义的调用方需自行按配置字段比较。 |
| `CustomLicenseManager(LicenseParam)` | 删除 | 替换为 `CustomLicenseManager(String,CustomKeyStoreParam,Preferences)`；固定 `license` 槽位迁移为 subject 槽位，旧安装状态不自动延续，需重新 `install` 已有文件；旧 `LicenseParam` 类型不再存在。 |
| `store(LicenseContent,File)` | 类型改变 | 替换为 `store(global.namespace.truelicense.api.License,File)`。 |
| `install(File)` / `verify()` | 返回类型改变 | 返回 4.x `License`，抛出 4.x `LicenseManagementException`。 |
| `uninstall()` | 保留卸载职责，存储键范围及异常类型改变 | 仅删除新 subject 槽位，不自动删除旧 `license` 槽位；使用 4.x `LicenseManagementException`。 |
| `LICENSE_SUFFIX` | 删除 | UI/文件选择调用方自行声明所需扩展名，不再从管理器继承 Swing 常量。 |
| `getLicenseParam/setLicenseParam` | 删除 | 管理器构造后配置不可变；通过新构造器创建不同 subject/key store/preferences 的实例。 |
| `create(LicenseContent): byte[]` | 删除 | 使用 `store(License,File)`；直接使用上游时调用 `VendorLicenseManager.generateKeyFrom`。 |
| `verify(byte[])` | 删除 | 使用 `ConsumerLicenseManager` 和 FunIO `Source/Store`；预览场景可配置 `BIOS.memory()` 临时存储。 |
| `getFileFilter()` | 删除 | UI 层自行配置文件过滤器；核心许可证模块不再暴露 Swing API。 |
| 继承的 `create/install/verify(...,LicenseNotary)` 钩子 | 删除 | 旧 GPL 父类扩展点不再二进制兼容，也不提供 shim。 |
| `validateCreate/validate` | 以 4.x 类型保留 | 参数改为 4.x `License`，异常改为 `LicenseValidationException`；高层消费者不应依赖内部钩子。 |

## 行为边界

- 生成端保留旧 `validateCreate` 行为：允许生成尚未生效的许可证；拒绝已经过期、结束时间早于开始时间、消费者类型为空的内容。
- 消费端保留 4.x 内建的 holder、issuer、consumer、日期和 subject 校验。
- consumer 上游缓存周期设为 0，确保同一 manager 用 B 替换 A 后，校验和返回的都是实际 B，
  不会用缓存 A 校验后再返回 B。
- user Preferences 节点继续只接受忽略大小写的 `user` 类型且 consumerAmount 必须为 1；
  system Preferences 节点接受任意非空类型和正数 consumerAmount。两者都继续叠加 4.x 日期、
  subject 等内建校验和自定义 `validate` 钩子。
- 4.x `ConsumerLicenseManager.install` 只验证签名，不校验许可证内容。因此适配器只有在
  `verify` 和 `load` 都成功后才返回安装成功；失败会尝试卸载，之后 `verify` 必须失败。
- 旧 1.33 manager 使用固定 Preferences 键 `license`，新 manager 使用 subject 作为 Preferences
  键；两者之间没有自动迁移。同一节点上的新 manager 也不会读取旧固定槽位，新 `uninstall()`
  只移除新 subject 槽位，旧槽位清理需要另行明确授权。
- 底层 manager 在每次安装或卸载尝试前先使当前实例的验证状态失效；只有完整安装成功才恢复。
  清理失败不会覆盖原始 checked/runtime/Error，清理异常作为 suppressed 保留。新建 manager
  仍可按设计读取持久化节点中可能残留的有效许可证，但仅限新 subject 槽位。
- 高层 `LicenseVerify.verify()` 同时要求最近一次安装成功。即使 Preferences 删除或失败安装清理
  抛出异常、旧 key 仍暂时残留，高层状态也会先失效并返回 `false`。
- 额外硬件模型仍是空扩展点；本迁移没有扩大 `LicenseExtraModel` 语义，也未修改独立的
  `ddd4j-extension-license` RSA 协议。

## 升级步骤与持久化状态边界

升级前保留原 `.lic` 文件和公钥库。使用正确的 subject、口令和签名算法，在新 manager
中重新安装该已有许可证文件并执行校验；这是重新安装已有文件，不是重新签发或重新签名。
如果只剩旧 Preferences 节点而没有许可证文件，应先在原旧版本的受控环境中找回或导出
原文件，再交给新安装流程；不要为补证访问、迁移或删除真实节点。旧 Preferences 槽位的
清理需要单独审批。

本轮只证明 Bean 属性以及源码/二进制调用签名的保留。`LicenseCreatorParam` 新增
`signatureAlgorithm` 尚无旧对象流跨版本测试，因此不能据此声称任意历史 Java 序列化 DTO
均可直接继续签发；本轮也未复现或宣称复现生产故障。调用方应显式设置所需配置并重新构建 DTO。

## TrueLicense 1.33 合成夹具

测试资源 `truelicense-legacy/legacy-v1.lic` 由隔离的 TrueLicense 1.33 classpath 生成；
对应 `legacy-public.jks` 只包含合成公钥，私钥库只存在于 `/tmp`，未进入仓库。
该历史夹具使用 1024-bit DSA + SHA1withDSA 许可证签名；公钥证书由当前 JDK 8 默认以
SHA256withDSA 自签。这一组合验证适配器必须显式使用许可证签名算法，不能错误地从公钥证书
签名算法推断许可证算法。

1.33 使用 SHA-1/DSA，无法用 2048-bit DSA 在当前 JDK 8 上签名；失败证据为
`InvalidKeyException: The security strength of SHA-1 digest algorithm is not sufficient for this key size`。
这只说明历史夹具的限制。4.x 新签发测试仍使用 2048-bit DSA + SHA256withDSA，未降低密钥强度，
也未修改 JDK 全局安全策略。

## 三线验证

三线均运行以下目标命令，未跳过 Enforcer：

```bash
mvn -B -ntp -Pparity-verification \
  -pl ddd4j-auth/ddd4j-auth-license -am \
  -Dtest=TrueLicenseMigrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

| 版本线 | JDK / Maven | 结果 | 原始证据 |
|---|---|---|---|
| 1.0.x | Corretto 8u504 / Maven 3.9.16 | 20 tests，0 failure/error/skipped | `.superpowers/sdd/2026-09-08-truelicense4-migration/task-1-logs/green-review-round1-main-raw.log`、`surefire-review-round1-main.xml` |
| 2.0.x | Corretto 17.0.20 / Maven 3.9.16 | 20 tests，0 failure/error/skipped | 同目录 `green-review-round1-v2-raw.log`、`surefire-review-round1-v2.xml` |
| 3.0.x | Microsoft JDK 21.0.12.1 / Maven 4.0.0-rc-6 | 20 tests，0 failure/error/skipped | 同目录 `green-review-round1-v3-raw.log`、`surefire-review-round1-v3.xml` |

测试 fork 在 JVM 启动时通过 `java.util.prefs.PreferencesFactory` 装载纯内存
`LicenseTestPreferencesFactory`，`BeforeAll` 在任何许可证操作前验证实际 user root 就是该工厂产物。
最新 `green-review-round1-*` 运行不依赖 `java.util.prefs.userRoot`，也不读取、输出或清理操作系统
默认用户 Preferences 节点。
内存工厂还可注入删除失败，用公开 `verify()` 行为证明卸载失败和失败安装清理失败均保持 fail-closed。
同一套测试还明确拒绝 `null` 与空白 `signatureAlgorithm`。

三线叶模块依赖树均只解析到 `global.namespace.truelicense:*:4.1.4`，没有
`de.schlichtherle.truelicense:*:1.x` 运行依赖。原始依赖树分别保存在同目录
`dependency-tree-main-raw.log`、`dependency-tree-v2-raw.log`、`dependency-tree-v3-raw.log`。

3.0.x 使用 Maven Model 4.1；误用 Maven 3.9.16 会在解析 `modelVersion=4.1.0` 和
`subprojects` 时失败。该非代码失败保存在 `failed-v3-maven3-raw.log`，最终成功证据来自 Maven 4。
Maven 4 对该线现有有效模型输出大量告警，目标模块仍完成编译和 20 项测试；告警不是本迁移新增的
许可证行为证明，应由三线模型治理独立跟进。

初始 API RED 与算法 API RED 当时只在工具会话中保留了完整输出，本目录仅保存事实摘要
`red-main.txt`、`red-signature-algorithm-api.txt` 和 `red-legacy-fixture.txt`，不得把摘要表述成原始日志。
Preferences 隔离门禁和高层 fail-closed 修复则保留了原始 RED：
`red-preferences-isolation-raw.log`、`red-high-level-fail-closed-raw.log`。
Round 1 的有效行为 RED 为 `red-review-round1-behavior-main-raw.log`：19 tests 中 7 failures，
覆盖 I1–I3。第一次 `red-review-round1-main-raw.log` 的 7 errors 只暴露同实例测试密钥口令前置问题，
不作为 I1–I3 复现证据；高层节点被移除时的 runtime RED 单独保存在
`red-review-round1-high-level-runtime-main-raw.log`。

在内存工厂引入前，早期高层测试曾以合成 subject 访问操作系统默认 `LicenseVerify` Preferences
节点；没有证据表明真实许可证受损，也没有为补证去读取或清理该节点。后续隔离证据不能追溯性地
证明早期运行从未访问默认节点。

## Task 2 最终验证补充

2026-09-08 的 Task 2 在三条 ddd4j 维护线上使用各自规定工具链执行了下列默认回归，命令没有
`-Dtest`，也没有跳过 Enforcer。执行到 `package` 是为了同时生成本轮 JAR；后续全仓结构脚本的
`clean compile` 清理 JAR 后，只调用叶模块 `jar:jar` 复用新编译 classes 重新打包，没有重复测试。

```bash
mvn -B -ntp -Pparity-verification \
  -pl ddd4j-auth/ddd4j-auth-license,ddd4j-extensions/ddd4j-extension-license \
  -am -Dsurefire.reportNameSuffix=t2-20260908T182602 package
```

| 版本线 | 工具链 | `TrueLicenseMigrationTest` | extension 既有测试 | 结果 |
|---|---|---:|---:|---|
| 1.0.x | JDK 8u504 / Maven 3.9.16 | 20 | `LicenseInfoTest` 3 + `LicenseKitTest` 4 | 27 项均无 failure/error/skipped |
| 2.0.x | JDK 17.0.20.1 / Maven 3.9.16 | 20 | 3 + 4 | 27 项均无 failure/error/skipped |
| 3.0.x | JDK 21.0.12.1 / Maven 4.0.0-rc-6 | 20 | 3 + 4 | 27 项均无 failure/error/skipped |

三线运行时依赖树的 `ddd4j-auth-license` 均解析为
`global.namespace.truelicense:{truelicense-v1,truelicense-core,truelicense-spi,truelicense-api,truelicense-obfuscate}:4.1.4`
及 Fun I/O 2.4.1，没有 `de.schlichtherle.truelicense:*:1.x` Maven 运行依赖。

许可证报告和 SBOM 只覆盖 `ddd4j-auth-license` 叶模块，不代表整个仓库。主线和 2.0.x 由
CycloneDX 2.9.3 直接生成 16/19 个组件。3.0.x 的真实 Maven 4 叶 POM 两次触发插件自身的
`Invalid Collect Request: null`；因此 Maven 4 原生 SBOM 门禁仍记为失败，而不是把构建降级为
Maven 3。替代证据先由 Maven 4 导出实际 runtime 依赖 JSON，再用只含 4 个直接依赖、必要版本约束
和作用域的最小公开 POM 调用 Maven 3 报告插件。最终 18 个组件在
group/artifact/type/classifier/version 上与 Maven 4 实际树逐项一致，18 项均为 compile/jar、无
classifier；`lombok` 的 optional 映射为 SBOM optional，其余 17 项映射为 required。完整 effective
POM 中间文件没有保留。该替代只解决元数据生成兼容性，不是 3.0.x Maven 4 原生 CycloneDX 通过。

三线 `THIRD-PARTY.txt` 与 SBOM 均先确认非空，之后才以对应 `DDD4J_REPORT_DIR` 调用现有
`verify-license-policy.sh`，三线均未发现策略阻断项。没有执行会清全局 Maven 缓存或清理报告目录的
`generate-sbom.sh` / `generate-license-report.sh`，也没有修改白名单隐藏结果。

本轮实际报告把 TrueLicense 4.1.4 的 5 个构件和 Fun I/O 2.4.1 的 3 个构件均识别为
Apache-2.0。上游依据限定为发布元数据，不构成更广泛的法律结论：

- [TrueLicense v4.1.4 发布说明](https://github.com/christian-schlichtherle/truelicense/releases/tag/v4.1.4)
  与 [v4.1.4 LICENSE](https://github.com/christian-schlichtherle/truelicense/blob/v4.1.4/LICENSE)。
- [TrueLicense 4.1.4 根 POM](https://repo.maven.apache.org/maven2/global/namespace/truelicense/truelicense/4.1.4/truelicense-4.1.4.pom)
  声明 Apache License, Version 2.0；`truelicense-v1` 通过父 POM 继承。
- Fun I/O 2.4.1 的实际 [API POM](https://repo.maven.apache.org/maven2/global/namespace/fun-io/fun-io-api/2.4.1/fun-io-api-2.4.1.pom)、
  [BIOS POM](https://repo.maven.apache.org/maven2/global/namespace/fun-io/fun-io-bios/2.4.1/fun-io-bios-2.4.1.pom)
  和 [SPI POM](https://repo.maven.apache.org/maven2/global/namespace/fun-io/fun-io-spi/2.4.1/fun-io-spi-2.4.1.pom)
  分别声明 Apache License, Version 2.0。

新鲜 JAR 的 8 个顶层生产类三线 `javap -public` 输出完全一致，并补入此前遗漏但未修改的
`LicenseExtraModel`。旧高层五参 Verify/Creator 示例和新低层六参 key-store/manager 示例均在
JDK 8/17/21 实际编译，但没有运行客户端或访问 Preferences。若外部调用方替换旧
`verify(byte[])`，原生 consumer 的授权校验必须按 `install → verify → load` 完整执行；仅
`install` 或 `install → load` 只能用于预览，不能等价为许可证有效性验证。

外部消费方还必须同步发布并使用更新后的 BOM 与 `ddd4j-auth-license`；本任务未执行发布，外部
发布状态未在本轮核验。已发现的外部 ddd4j-boot / ddd4j-quarkus 旧 1.33 声明属于另外仓库，仍等待用户选择后续
范围，本次没有修改，也不能宣称已迁移。三线叶 POM 都只声明无数字版本的 `truelicense-v1`，
版本集中为 4.1.4；主线模块描述仍是 `Ddd4j License Component`，而 2.0.x/3.0.x 带同步说明，
这是本轮只读审计发现的非行为差异，未擅自修改 POM。

Task 2 的最终独立审查及 I4 文档定点复审已完成，报告保留于本计划证据目录的
`final-review.md`、`final-doc-review.md`。上述证据只说明目标三线与目标模块检查结果，不表示外部仓库、
整体反应堆、发布流程或生产上线已经验收。

框架消费侧维护线审计见 [TrueLicense 4.x 框架消费侧审计](truelicense4-framework-consumer-audit.md)：
Boot 13条维护线仍重复引入1.33且未透传签名算法；Quarkus两条维护线在独立extension-license之外
仍带无用1.33；Javalin三条维护线无旧依赖，Cloud无直接许可证模块。本轮未修改外部仓库。

## 收尾时的仓库状态

本任务没有执行 Git 提交/推送。期间检测到其他操作将迁移代码连同既有修改纳入提交：当前
三线 HEAD 分别为 `6d217054`、`90ab6cc3`、`2e730fb4`，不是最初的审查基线。
主控依据原始快照和最终审查 diff 重建并比对 48 个文件：除 2.0/3.0 BOM 后续增加的无关
t-io 管理项及 3.0 Jackson annotations 属性外，受审查文件与当前内容一致。
3.0 实际解析的 jackson-annotations 仍为已测试的 2.22，未因新增属性改变。
本指南和实施状态的最后更新仍在工作区中，不应表述为本任务已经提交、推送或发布了成果。
