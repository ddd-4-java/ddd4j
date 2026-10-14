# 三线依赖清单与验证

本目录记录 JDK 8 / 17 / 21 三线的依赖治理结果。规格事实源仍为
`ddd4j/docs/superpowers/specs/2026-09-06-three-line-parity.md` 的 D1。本次不是全量兼容性验收通过状态。

- `alignment-policy.json`：原始组件基线、已核验坐标替代，以及明确未完成的兼容/迁移项。固定基线避免通过删除声明或移入 profile
  缩小检查范围。
- 第三方库版本不从 DDD4J 分支号推导；只有 `dependency_versions` 中显式配置的坐标才校验指定版本（字符串表示三线共享版本，JDK 键表示明确的平台版本）。EasyExcel 五个发布模块统一固定为正式版 `5.0.1`。构件解析、JAR 字节码、组件覆盖和漏洞扫描门禁继续执行。
- `version-evidence.json`：部分兼容发行版的发布 JAR 字节码证据。
- `component-matrix.csv`：历史清单快照，用于比较；其中状态可能已过期。当前三线有效版本与检查状态以本次严格检查输出的 `components.csv` 和 `result.json` 为准。
- SOAP 工具库实际制品为 `io.github.easy4j:soap-utils`；旧 BOM 中的 `soaputils` 是错误坐标。三线分别使用 1.0.x、2.0.x、3.0.x 构件，并由本线 JDK 验证字节码和单元测试。
- 用户确认 `io.github.easy4j:disruptor-biz` 旧坐标归并到 `io.github.easy4j:disruptor-extension`。JDK 17/21 BOM 只管理各自版本的实际扩展构件；JDK 8 BOM 原本未声明 Easy4J 的这两个坐标，不因此新增依赖。历史组件矩阵仍保留旧快照，当前状态以严格依赖检查为准。
- PF4J 旧坐标 `io.github.easy4j:pf4j3-extension` 已并入上游 `pf4j-extension` 项目；JDK 17/21 的有效构件分别是 `pf4j-extension-core`、`pf4j-extension-spring`、`pf4j-extension-update`，按实际能力选择。聚合 `pf4j-extension` 是 POM 而非 JAR。JDK 8 线当前未声明这些组件；此坐标迁移不改变其第三方依赖。
- Spring Security 旧坐标 `io.github.easy4j:spring-security-extension2` 的类已并入上游 `spring-security-extension`；JDK 17/21 BOM 管理本线的实际构件，JDK 8 线不新增该依赖。三线的 Spring Security 版本和其他第三方依赖仍各自独立。
- `io.github.easy4j:shiro-biz-sdk` 的源码归属是 `shiro-extension`；上游将原 Shiro Biz 实现拆分为 `shiro-extension-core` 与 `shiro-extension-spring`。JDK 17/21 BOM 已管理本线的两个实际构件，旧坐标按两模块映射；JDK 8 线不新增。另有 `com.github.redacted-legacy-family:shiro-biz` 历史依赖，仍按其原坐标独立管理。
- `io.github.easy4j:security-guard-sdk` 的上游 2.0.x/3.0.x 均无生产源码，目前只是规划中的 SDK；ddd4j 没有模块直接使用。BOM 暂不管理空构件，策略保留历史坐标并明确排除原因；这不表示安全防护能力已实现。
- `unverified-artifacts.csv`：尚未取得有效 JAR 检查结果的坐标；不等同于确认不存在。
- `validation-result.json`：严格门禁的原始结果；非零错误不能作为已完成处理。
- 新版检查器在 `--output` 下另写 `module-usage.json`：把未验证构件按精确 type/classifier 关联到模块 POM 默认 `<dependencies>`；仅用于优先排障，不改变 `--strict-artifacts` 失败判定。该清单不覆盖继承、profile 或传递依赖，未匹配不等于构件可豁免。

三线普通第三方依赖按各自 JDK/框架生态独立选择坐标和版本；检查器只要求其在本线的显式声明或历史基线得到有效管理。跨线覆盖仅适用于 Easy4J 组件和策略中逐项登记的坐标迁移。`--strict-artifacts` 仍检查列入清单的本地 JAR，缺失保持未验证；默认模块未直接引用不等于传递依赖或远端构件已通过。

## 复现

在每个 checkout 使用该线 JDK 与已有 `mvnw`，生成新的有效 POM：

```sh
./mvnw -N -f ddd4j-dependencies/pom.xml help:effective-pom -Doutput=/tmp/ddd4j-effective-8.xml
```

另外两线分别输出 `/tmp/ddd4j-effective-17.xml`、`/tmp/ddd4j-effective-21.xml`。从三个 checkout 的父目录运行：

```sh
python3 ddd4j/scripts/verify_dependency_alignment.py \
  --roots ddd4j ddd4j-v2.0.x ddd4j-v3.0.x \
  --effective /tmp/ddd4j-effective-8.xml /tmp/ddd4j-effective-17.xml /tmp/ddd4j-effective-21.xml \
  --policy ddd4j/config/dependencies/alignment-policy.json \
  --strict-artifacts --output /tmp/ddd4j-dependency-check
```

缺少本地 JAR 时，可在对应 checkout 使用已有 Maven 仓库配置执行
`dependency:get -Dartifact=groupId:artifactId:version -Dtransitive=false`，再复查。此次补充下载的公开 JAR 缓存在
`/tmp/ddd4j-dependency-alignment/jars`；可使用 `--extra-cache` 指向该目录。临时缓存不属于交付文件，也不会被提交。

门禁回归：

```sh
python3 -m unittest discover -s ddd4j/scripts -p test_dependency_alignment.py -v
```

依赖属性固定为三个分区：`Global Properties`、`Third-Party Dependencies`、
`Maven Dependencies`。每段按属性名进行大小写不敏感的自然字母排序。
`io.github.easy4j` 组件的版本属性不保留 `easy4j-` 前缀。发生名称冲突时，
删除重复的 `com.github.redacted-legacy-family` 旧坐标和旧版本属性，只保留当前 easy4j 组件。

检查与重新格式化：

```sh
python3 ddd4j/scripts/verify_dependency_property_layout.py \
  ddd4j/ddd4j-dependencies/pom.xml \
  ddd4j-v2.0.x/ddd4j-dependencies/pom.xml \
  ddd4j-v3.0.x/ddd4j-dependencies/pom.xml

PYTHONPATH=ddd4j/scripts python3 ddd4j/scripts/format_dependency_properties.py \
  ddd4j/ddd4j-dependencies/pom.xml \
  ddd4j-v2.0.x/ddd4j-dependencies/pom.xml \
  ddd4j-v3.0.x/ddd4j-dependencies/pom.xml
```

## 状态的边界

- `bytecode-pass` 只证明检查到的基础 class 文件没有超过对应 JDK；不能证明传递依赖、反射、框架接口、自动配置或业务行为兼容。
- `bytecode-empty` 表示 Easy4J 普通 JAR 没有基础 class；空壳不能证明组件实现或字节码兼容，严格检查报错。纯资源/原生库等第三方 JAR 不受此规则影响。
- `replacement` 表示明确记录的组件坐标迁移或模块合并，不表示任意旧 SDK 与新 SDK 自动等价。
- `unsupported` / `manual-migration` 不计为支持，并使门禁失败。包括客观 JDK 限制、待验证的旧发行线，以及需要新框架适配产物的能力。
- `unverified-artifact` 不计为通过；严格模式下使门禁失败。可能是仓库缺失、错误版本、历史模块迁移或尚未下载。
- `absent` 主要表示后续 JDK 线引入的组件未在更低版本线提供，不能直接等同于业务能力缺失。
- `pom` 是上游真实发布的聚合 POM，不应当作可执行 JAR。

上游 BOM 间接导出的历史组件不机械求并集；本项目显式维护的组件以有效 POM 检查覆盖。所有 profile 都进入检查全集。测试分类器与主
JAR 分开处理。多版本 JAR 的 `META-INF/versions` 及 `module-info.class` 不作为基础 Java 版本超限证据。

Profile 枚举用于防止隐藏组件；各条件版本仍须在对应激活条件下生成 effective POM 并单独验证，默认模型不能证明所有条件分支运行兼容。

## 每周依赖审计

OFDRW 2.4.0 在三线管理 `org.ofdrw:ofdrw-core`。`ofdrw-full` 包含转换器，它声明 PDFBox 2.0.27，而三线 BOM 管理 PDFBox 3.0.7；完整转换能力须先做针对性集成测试，不能仅凭 Java 8 字节码将其列为兼容。


`.github/workflows/weekly-dependency-audit.yml` 每周一 UTC 02:17 检查 `feature/1.0.x`、`feature/2.0.x`、`feature/3.0.x` 各自的 BOM，并运行 Trivy 文件系统漏洞扫描。GitHub 的 `schedule` 只从默认分支加载工作流，因此需把该工作流交付到默认分支后才会自动运行；三个功能分支上的 `workflow_dispatch` 可供手动验证。

`config/dependencies/weekly-update-policy.json` 的 `fixed` 是逐线精确版本，配置版本漂移会失败；`excluded` 是有理由的不做最新版本比较的坐标，包括各线独立适配的 Spring、Jackson、Easy4J 和快照版本。规则以外的每个直接管理坐标都查询 Maven Central 最新稳定发布版（仅有预览版时取最新预览版），落后、元数据不可解析或查询失败都会使该线失败。报告保存在 `target/dependency-audit/version-report.json`；CI 不自动改写 POM，升级仍需按各线 JDK、框架、传递依赖和测试分别验证。

漏洞扫描使用 `aquasecurity/trivy-action@v0.36.0` 分别扫描三线 Maven POM，并从各线 BOM 生成 CycloneDX 清单扫描全部直接管理版本；HIGH/CRITICAL 漏洞使任务失败，无需 NVD 密钥。`verify_weekly_vulnerability_report.py` 检查 POM 报告非空，且 SBOM 报告覆盖清单中的全部管理版本，防止 Maven 4 根 POM 未识别时误报通过。两份报告保存为 `target/dependency-audit/trivy.json` 和 `target/dependency-audit/managed-bom-trivy.json`。版本新旧检查不能代替漏洞检测，扫描结果也不能证明升级后的兼容性；私有仓库无法解析的传递依赖仍须单独核查。

本地运行：

```sh
python3 scripts/test_weekly_dependency_versions.py -q
python3 scripts/check_weekly_dependency_versions.py \
  --pom ddd4j-dependencies/pom.xml \
  --policy config/dependencies/weekly-update-policy.json \
  --line 3.0.x \
  --output target/dependency-audit/version-report.json
```
