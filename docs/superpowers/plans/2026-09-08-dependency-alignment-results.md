# 三线依赖组件清单与 JDK 版本治理结果

状态：**部分实施并验证，严格门禁未通过；不能宣称三个版本已全部完成兼容性对齐。**

事实源：`../specs/2026-09-06-three-line-parity.md` 的 D1。未创建或切换分支，未使用 worktree，未提交、推送或发布。JDK8 checkout 保持原 detached HEAD。

## 本轮已落地

- 修改三条线的 `ddd4j-dependencies/pom.xml`，依据真实 effective POM 区分直接声明、BOM 覆盖和缺项。
- 补齐一批向上缺项；将 43 个已确认基础字节码兼容的组件补入 JDK8，向 JDK17 补入 OpenHTMLToPDF 系列。
- 统一 22 项普通组件的版本漂移，保留 Guice、R2DBC 等有框架/API 约束的既有组合，不只按 class 主版本机械升级。
- 修正 JDK8 的 CXF、Tika、jOOQ、Docx4j、HikariCP、Flowable、Resilience4j、数据库驱动及其他已验证发行线；修正 JDK17 的 jOOQ、Derby、Hive、CAS Client、Pi4J 等。
- 核对发布 POM 后，将 13 类错误声明为 JAR 的聚合组件改为 `type=pom`，包括 hitool、easydoc/easypdf、部分 easy4j 聚合项目、HttpComponents 和 Grizzly 聚合项目。
- 修正 Feign 11 的 form 模块坐标、JSON Path Assert 的错误 groupId、Spring 7 的 spring-jcl 旧坐标，以及 CAS/Jakarta/Thymeleaf/MyBatis Plus 的适配坐标。
- SkyWalking toolkit 独立使用真实发布的 9.7.0，不继续引用未发布的 10.0.1。
- ZXing 新旧坐标核对了 39 个公开类/API descriptor，完成 JDK17/21 中英文 QR 编码解码对比。17/21 统一使用已发布、已验证的 easy4j 2.0.x 产物。详见 [迁移证据](../../../config/dependencies/zxing-coordinate-migration.md)。
- Java8 默认 SLF4J2 + Logback1.3 配对；Dropwizard2、Boot2相关测试保留显式 `logback-legacy.version`。父 POM 的 Expressly 改为匹配 javax EL 的 GlassFish 实现。
- 三条线的 `<properties>` 统一为全局、第三方依赖、Maven依赖三段，并在每段按自然字母顺序排列。2.0.x/3.0.x 的 `easy4j-*.version` 前缀已全部移除；冲突的 `com.github.hiwepy` 旧坐标和旧属性已删除，仅保留当前 `io.github.easy4j` 组件及对应 2.0.x/3.0.x 版本。
- 删除全部临时 `alignment.*` 属性。`hitool-crypto`、`hitool-mail` 改用本线 `${hitool.version}`；补填的 `mybatis-spring-boot-starter`、`jooq-plus`、`mybatis-plus-enhance`、旧 `shiro-redis`、旧 `spring-javassist`、`ip2region-spring-boot-starter` 从 ddd4j 通用依赖清单删除。Spring Boot starter 归属对应 ddd4j-boot 版本线。

### 代表性版本矩阵

| 组件 | JDK8 | JDK17 | JDK21 |
|---|---|---|---|
| Spring Framework | 5.3.39 | 6.2.19 | 7.0.8 |
| Hutool | 5.8.47 | 5.8.47 | 5.8.47 |
| Guava | 33.6.0-jre | 33.6.0-jre | 33.6.0-jre |
| CXF | 3.5.11 | 4.2.2 | 4.2.2 |
| Tika | 2.9.4 | 3.3.2 | 3.3.2 |
| jOOQ | 3.14.16 | 3.19.30 | 3.21.6 |
| Docx4j | 8.3.15 | 11.5.14 | 11.5.14 |
| HikariCP | 4.0.3 | 7.1.0 | 7.1.0 |
| Flowable | 6.8.1 | 7.2.0 | 7.2.0 |
| Caffeine core | 2.9.3 | 3.2.4 | 3.2.4 |
| Caffeine simulator | 2.9.3 | 3.1.8 | 3.1.8 |
| SLF4J | 2.0.18 | 2.0.18 | 2.0.18 |
| 默认 Logback | 1.3.14 | 1.5.36 | 1.5.36 |
| SkyWalking toolkit | 9.7.0 | 9.7.0 | 9.7.0 |

表格不能替代逐项验证。完整 [组件矩阵](../../../config/dependencies/component-matrix.csv) 包含当前有效版本、替代、受限和未验证状态；[版本证据](../../../config/dependencies/version-evidence.json) 记录部分代表产物的字节码及摘要。

## 严格门禁结果

固定原始基线为 1,314 个组件、1,338 个 type/classifier 变体。当前矩阵有 1,345 组坐标/变体，形成 4,035 条“组件 × JDK”记录：

| 状态 | 条目数 | 含义 |
|---|---:|---|
| bytecode-pass | 3,470 | 已检查基础 class 主版本；不代表传递依赖、框架或所有业务行为通过 |
| replacement | 62 | 有明确证据的坐标迁移或模块合并 |
| excluded | 8 | 经边界确认不属于 ddd4j 通用依赖的旧组件 |
| pom | 34 | 聚合 POM 类型 |
| absent | 240 | 主要是较新版本线才引入的组件/变体，未要求机械向下复制 |
| unsupported | 20 | 当前选型受 JDK 限制或仍需组合验证 |
| manual-migration | 6 | 旧 starter 在17/21两条线需要真正的框架迁移 |
| unverified-artifact | 195 | 尚未取得当前版本/分类器的完整产物检查结果 |

严格运行返回 **221 个错误：26 个受限/迁移条目 + 195 个未验证条目**。未验证条目对应 **81 个唯一坐标/版本组合**。没有把这些条目计为通过。

除上述明确记录项，当前门禁未报额外向上清单缺口、未解析版本变量或已检查基础 class 的 JDK 超限。该结论只覆盖本门禁边界，不是全量传递依赖与生产验收证明。

曾尝试解析 248 个未缓存的精确坐标，149 个成功。其余失败中一部分已依据上游真实 packaging 修正为 POM，一部分仍是错误版本、历史模块迁移或仓库产物问题。当前 [未验证产物清单](../../../config/dependencies/unverified-artifacts.csv) 保留精确坐标与 probe 状态；“未验证”不等于证明该组件永远不存在。

## 尚未完成的事项

1. **旧 starter 的真实迁移。** `security-jwt-spring-boot-starter`、`security-biz-spring-boot-starter`、`redistpl-plus-spring-boot-starter` 的既有产物绑定 Boot2/旧自动配置。新 SDK 公开能力和自动配置并不等价，部分新 JWT/security 产物仍引用 javax Servlet。已移除本轮向17/21错误补入的旧 starter，并记录 manual-migration；不能以候选 SDK 坐标存在就标为对齐。
2. **JDK受限或组合待验证项。** 例如 OSHI FFM 需要更高 JDK、Oracle R2DBC 需要Java11、Spring Pulsar 当前发行线需要Spring6。PAC4J SAML已找到Java8的3.9.0候选，但不能与当前4.x core/Buji直接混装，需要整体组合验证。清单中包含客观平台限制，也包含仍可继续验证的旧发行线，不应统称为“无解”。
3. **81组产物的逐项确认。** 包括厂商仓库/未发布产物，以及公开坐标世代错误。已确认的后续候选：
   - 旧 `io.swagger` 模块没有 `2.0.0-rc2`；`1.6.16`存在，但使用javax/Jackson2，不能机械放入Jakarta/Jackson3线。
   - `com.alibaba:dubbo:3.3.6`不存在；真实坐标为`org.apache.dubbo:dubbo:3.3.6`，需核验Spring/Servlet集成组合。
   - Jetty旧`websocket-*`模块不能配12.1.8；应按Jetty API与Jakarta EE10/EE11选择新模块，不是直接字符串替换。
   - Resilience4j旧`spring`、`spring-boot2`、`spring-cloud2`没有2.4.0；Spring6模块已拆分，Spring7及自动配置能力仍需核验。

这部分需要进一步验证兼容组合，或取得/迁移上游产物；本轮未编造版本，也未通过删除固定基线、自动激活高JDK profile或放宽门禁取得表面通过。

## 测试与审查

| 验证 | 结果 | 本地日志 |
|---|---|---|
| 三线实际JDK下 effective POM | 全部 BUILD SUCCESS | `/tmp/ddd4j-dependency-alignment/model-final-{0,1,2}.log` |
| 三线 reactor validate | 全部 BUILD SUCCESS | `/tmp/ddd4j-dependency-alignment/validate-final-{0,1,2}.log` |
| JDK8 全部 samples及依赖 | 1,386，0失败/错误/跳过 | `/tmp/ddd4j-dependency-alignment/final-samples2-8.log` |
| JDK17 全部 samples及依赖 | 1,708，0失败/错误/跳过 | `/tmp/ddd4j-dependency-alignment/final-samples-17.log` |
| JDK21 全部 samples及依赖 | 1,825，0失败/错误/跳过 | `/tmp/ddd4j-dependency-alignment/final-samples-21.log` |
| 本轮JDK21完整reactor | 2,257，0失败/错误/跳过 | `/tmp/ddd4j-dependency-alignment/reactor-2.log` |
| 门禁自身回归 | 9项通过 | `scripts/test_dependency_alignment.py` |
| ZXing实际二维码往返 | 三组输入在17/21下结果一致 | `/tmp/ddd4j-dependency-alignment/zxing-behavior-parity.txt` |
| 严格依赖门禁 | **未通过** | `config/dependencies/validation-result.json` |

测试数来自对应日志的class报告，不对有重叠的不同运行求和。完整reactor和samples运行发生在本轮多个检查点；最终独立坐标修正另有发布JAR/API/行为探针和模型验证，不能把一次构建成功外推为全部上游组件验收。

独立只读审查发现的三个P1已修复并复核：移除自动高JDK profile；移除17/21错误加入的Boot2 starter；将固定基线及所有profile纳入门禁。另补充Kafka测试分类器、Netty仅分类器产物的回归，避免误报缺失或通过删除变体缩小全集。

用户/其他任务并行修改的Web上下文、可观测性与truelicense等文件未被回退或纳入本次改动声明。全工作区diff检查曾报告其他任务文件的EOF空行，本次只修复自己负责的文件。
