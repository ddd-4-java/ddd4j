# 中文 Javadoc 零缺口填充进度（feature/2.0.x / JDK 17 线）

- 日期：2026-10-09
- 分支：feature/2.0.x（worktree：ddd4j-2.0.x）
- 门禁：maven-javadoc-plugin doclint=all + failOnWarnings=true + failOnError=true（详见 javadoc-gate-evidence-20261009.txt）
- 批次约束：每批 ≤30 个文件

## 一、扫描口径（经 JDK 17 javadoc -Xdoclint:missing 实测标定）

扫描器：临时目录 scan2.py（行级扫描 + 标签级校验），经 5 组探针文件与 javadoc 真实输出逐条比对
修正后规则：

1. public/protected 类型（class/interface/enum/record）、方法、构造器、字段必须有 `/** ... */` 注释；
   注释与声明之间允许空行、`//` 行注释、注解（均不打断关联）；上一声明的注释不满足下一声明。
2. 接口 / @interface 的隐式 public 成员（方法、常量、注解元素）与枚举常量同样计入。
3. 标签级要求（缺失同样计入缺口）：
   - 每个方法参数 `@param`（含泛型参数 `@param <T>`）
   - 非 void、非构造器的返回值 `@return`
   - `throws` 声明的每个异常 `@throws`
4. 类型级仅要求注释本身（类类型参数不要求 `@param`，record 组件不要求）。
5. 门禁只覆盖 src/main（javadoc jar 范围）；src/test 计数单独列出，不计入主线清零目标。
6. 2026-10-09 补充修正：跨行声明第 2 行至结束行的大括号计入深度（此前多行构造器的 `{`
   落在非首行会漏算深度、类型栈提前弹出，构造器被误判为方法并多报 `no-@return`）。
   修正后基线口径以下方“刷新基线”为准。

## 二、基线（2026-10-09 扫描，全仓 1613 个 Java 文件）

全仓：type have=1263/missing=139；method have=1234/missing=5375；field have=315/missing=353。

主线 src/main（1251 文件，门禁相关）：

| 维度 | have | missing | 其中 no-comment | 其中仅标签缺口 | 涉及文件数 |
|---|---|---|---|---|---|
| type | 1228 | 117 | 117 | 0 | 90 |
| method | 1232 | 4732 | 3750 | 982 | 877 |
| field | 313 | 342 | 342 | 0 | 76 |

src/test（362 文件，不进门禁，仅备案）：type missing=22；method missing=641；field missing=11。

type 缺口文件分布：非 samples 47 个（合计 217 处声明），samples 43 个（合计 69 处声明）。

## 三、批次执行记录

> 规则：单批 ≤30 文件；批内文件的全部缺口（type/method/field 一并）清零后记“完成”。

| 批次 | 范围 | 文件数 | 完成时间 | 状态 |
|---|---|---|---|---|
| 批次 1 | 非 samples type 缺口（按路径排序前 30，另并入 1 个 field 缺口文件） | 31 | 2026-10-09 | 完成（复扫 0 缺口） |
| 批次 2 | 非 samples type 缺口（其余 17） | 17 | 2026-10-09 | 完成（复扫 0 缺口） |
| 批次 3 | samples type 缺口（第一批 ≤30） | ≤30 | - | 未开始 |
| 批次 4+ | 剩余 method/field 缺口（按模块推进） | - | - | 未开始 |

### 批次明细

**批次 1（31 文件，全部复扫 0 缺口）**：DataScopeAutoConfiguration、DefaultLicenseAutoConfiguration、
LicenseProperties(auth)、LicenseProperties(extension)、DataSizeKit、LotteryKit、AuthPrincipal、ThreadContext、
Ddd4jTenantContext(mybatis)、Ddd4jTenantContext(mybatisplus)、BaiduRegionTemplate、LicenseVerificationResult、
BaseMonitorProperties、PluginInvokeException、AuthcExtensionPoint、CryptoExtensionPoint、
DatasourceCryptoExtensionPoint、ParamSignatureExtensionPoint、ServletRequestExtensionPoint、
ServletResponseExtensionPoint、CglibProxy、JdkProxy、PluginUtils、QrCodeBlockElement、QrCodeFrame、
QrCodeImageElement、QrCodeTextElement、MQListener、BearerSubjectAuthenticator、WebIdempotencyLifecycle、BodyParam。

**批次 2（17 文件，全部复扫 0 缺口）**：web-javalin 的 Context/CookieParam/FormParam/HeaderParam/PathParam/QueryParam，
web-validation 的 AllowableValues/NumberValue/PhoneNumber/StringDateValue，webflux 的
DefaultSequenceConfiguration/DefaultWebFluxConfiguration/DefaultWebFluxConfigurer/LocalResourceProperteis/
SequenceProperties/I18nResourceBasenameHandler/WebFluxKit。

**批次 1-2 合计缺口消除（相对第二节基线的净变化）**：type −48（117→69）、method −135（4732→4597）、
field −18（342→324）。method 净变化已包含扫描器深度口径修正带来的新识别项（毛填充量高于净值）。

### 刷新基线（批次 1-2 完成后复扫，src/main）

| 维度 | have | missing | 涉及文件数（缺口文件） |
|---|---|---|---|
| type | 1276 | 69 | 43（全部为 samples） |
| method | 1408 | 4597 | 838 |
| field | 319 | 324 | 69 |

注：src/main 已无非 samples 的 type 缺口；method/field 缺口含少量批次文件外的新识别项
（如 webmvc/SequenceProperties 的 PREFIX 字段），全部并入批次 4+ 跟踪。

## 四、未完成清单（随进度更新）

- method 级缺口 4597 处（src/main）：主剩余项，批次 4+ 按模块推进。
- field 级缺口 324 处（src/main）：并入批次 4+。
- type 级缺口 69 处（samples 43 文件）：批次 3 目标（单批 ≤30 文件）。
- src/test 缺口（type 22 / method 641 / field 11）：不在门禁范围，按需另行安排。
- 说明：门禁全绿需 src/main 全部缺口清零；本进度文件按批次如实累计，未完成部分如实列示。
