# 1.0.x / 2.0.x / 3.0.x 共同契约与收敛规格

状态：第一批已完成并通过目标回归，后续批次未完成。事实源为本文件；三个版本按相同契约执行，不复制互相冲突的规格。

## 基线与边界

- 1.0.x：`../ddd4j`，6ee5879c78a36c5a8ecfbb40c930404d2602f117，JDK 8。
- 2.0.x：`../ddd4j-v2.0.x`，ebef4ed9ab1485ea924e0523187e522d717e74cc，JDK 17、Jackson 2。
- 3.0.x：`../ddd4j-v3.0.x`，1cce1a91eaeb6edad6b34db09579533878751233，JDK 21、Jackson 3。
- 允许 JDK 版本要求的语法、Maven 依赖、Jackson、sample/Quarkus 差异。允许原因必须对应具体路径与代码，不覆盖业务行为变化。
- 非豁免目录、包、对象、方法、参数名/类型/顺序、返回类型、可见性须收敛。CodeGraph 原始差异必须结合 record/Lombok 生成成员核验。
- 同输入的输出、异常、事件、数据库写入、状态副作用和资源释放遵守共同契约；任一分支通过自己的测试不代表跨分支一致。
- 保留已有公开调用方式：相同对象同时提供 record 风格访问器与既有 bean getter，避免通过删除 API 达成表面一致。
- 不创建 worktree、不切换分支、不覆盖用户修改；不提交、推送或发布。
- 延续现有 Superpowers 规格体系，不初始化 OpenSpec/Spec Kit。

## 第一批验收契约

### V1：四个已复现值对象

对象路径：

- `ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/ProjectionRunInfo.java`
- `ddd4j-data/ddd4j-data-mybatis/src/main/java/io/ddd4j/data/mybatis/adapter/SqlObservation.java`
- `ddd4j-mq/ddd4j-mq-redis-stream/src/main/java/io/ddd4j/mq/redisstream/RedisStreamRecord.java`
-

`ddd4j-extensions/ddd4j-extension-qlexpress/src/main/java/io/ddd4j/extension/qlexpress/model/QLExpressValidationResult.java`

所有构造参数与顺序以现有 record 组件为准，公开构造器一致；同时保留 bean getter 与组件访问器。相同全部字段应值相等，hashCode
一致；不同字段应不等。RedisStreamRecord 的 nativeMessage 必须参与 equals/hashCode。引用字段为 null 时 equals/hashCode
不得抛异常。toString 采用现有 record 格式，业务字段全部列出。集合字段本批不改变现有快照/引用语义。

### W1：Web 拦截器

非空 BaseWebInterceptor 列表全部注册，保持每项 pathPatterns/excludePathPatterns；空列表和 null 列表不注册，不抛异常。

### W2：请求头异常与 Feign 默认值

MissingRequestHeaderException 在国际化开启/关闭两条路径均返回 SC_MISSING_REQUEST_HEADER；不能直接沿用任一分支非国际化路径的旧错误码。

开启 autoFillSystemId 且无 Web 请求覆盖值时，ThreadContext 中 null/空串统一写入三个系统 ID header 为
"0"；非空值原样保留。空白串策略本批沿用已有 2.0/3.0 的 hasLength 语义，不擅自扩展到 trim。

### L1：默认操作日志

三个版本提供相同 DefaultApiOperationLogProvider 对象和方法；Guice 默认绑定真实实现。成功/失败回调进入 doApiOperationLog，保留
saveLog 扩展点及 Hidden 处理。1.0 只做 javax 等依赖适配，禁止用空接口实例代替。恢复实现不得建立新的循环依赖。

### M1：MQ 空白参数

ONS consumer group/topic 使用 hasText：空白监听器值回退配置值，最终仍为空白时在创建 Broker 连接之前拒绝。TDMQ 空白监听器
group 回退 defaultGroup；可通过注入 BrokerSubscriber 检验，不连接生产 Broker。

## 后续批次（不在本批完成声明范围）

第二批：EventStore 同步/异步对象拓扑、四方法签名、事件时间与版本副作用、payload 未知字段、分页与事务边界。第三批：非豁免缺失对象、外部
HTTP 参数以及框架上下文传播；禁止直接删除 HttpClient 参数以实现 JDK8 适配。第四批：剩余 record、集合
null/复制/可变性、字符编码与规则返回值。第五批：全目录/API/行为差分门禁。

## 验证标准

每个修复先有真实失败证据，再修改实现并复测；测试与目标源码按版本匹配编译，禁止复用旧 target 类冒充新源码。验收记录明确区分值对象隔离测试、目标模块
JUnit、受影响模块回归、全量 reactor。没有执行的门禁不得标通过。

## D1：三线依赖组件清单与 JDK 基线（2026-09-08 用户批准）

状态：实施中，严格门禁尚未通过；结果见 `../plans/2026-09-08-dependency-alignment-results.md`。

- 以三个 `ddd4j-dependencies/pom.xml` 显式维护的组件并集建立清单。低 JDK 线已有能力须在高 JDK 线保留，坐标迁移须记录替代关系。
- 使用 Maven effective POM 判定 BOM 覆盖，不以文本中没有直接声明判定缺失；上游 BOM 间接导出的历史组件单独统计，不机械复制已废弃模块。
- 版本按 JDK 8/17/21 及 Spring 5/6/7、Jackson 2/2/3 兼容组合确定。普通库无兼容原因时保持同版；不凭版本号大小推定 JDK 兼容。
- 没有适用发行版的组件必须给出具体 JDK/生态限制，不添加不可用版本冒充对齐。旧坐标改名、模块合并和 BOM 管理须显式说明。
- 验证至少包括：清单覆盖、属性解析、重复坐标、产物可解析性、JDK 字节码基线，以及受影响模块的真实回归。多版本 JAR 的高版本目录不作为基础
  JDK 违规；可选高版本实现需逐项核查。
- 保留当前 checkout、未提交修改及各线 Maven 模型；本次不提交、推送、发布，不初始化新规格体系。

## D2：三线产品收敛的增量契约（2026-10-10）

状态：实施中；本节扩展此前后续批次，历史第一批结论与 D1 的未通过状态保持原义。执行任务与验收顺序见 `../plans/2026-10-10-three-line-product-convergence.md`。旧基线 SHA 和目录只描述当时的验证输入；本轮三线起点及实际工作副本以 T00 快照为准。

### 共同能力与平台边界

- 三线是同一产品的 JDK 8、17、21 兼容发行线。共同领域模型、对象/公开方法、输入输出、错误、事件、资源生命周期、测试场景与产品文档应一致；采用现存有效实现的兼容并集，不能删除接口、测试或文档来降低差异数。
- 三线第三方依赖的集合、版本、Maven 配置及框架 API 按 JDK 8/17/21 各自独立选择，不以同坐标、同版本或同实现为收敛目标；三线必须相同的是 ddd4j 自有业务逻辑与可观察契约。以下平台例外只处理确实受 JDK/框架限制的公开入口，不能豁免相同逻辑的行为差异。
- `EventPayloadSerializer` 使用调用方显式给出的事件类型回读历史 payload；新增的未知字段在三线均忽略，已知字段仍回读，传入的 source mapper 配置不被修改。序列化产物不引入 `@class` 多态标记；历史载荷中出现的 `@class` 只作为未知字段忽略，不得改选目标对象类型。
- EventStoreDB 回读事件时，损坏的 JSON 元数据及 JSON 字面量 `null` 必须在载荷反序列化前以 `IllegalStateException("Invalid ESDB event metadata")` 失败；不能吞掉解析错误并把空 Map 当作有效元数据。三线分别使用自身 Jackson 版本。
- `JsonKit` 的 Jackson 2/3 适配应保持 `LocalDate`、`LocalDateTime`、`Instant`、`Duration` 的公开 JSON 字符串格式与反序列化值一致；单个日期时间场景通过不代表整个工具类已完成收敛。
- Maven 版本、编译目标、第三方坐标与命名空间、受 JDK/框架约束的适配实现可以不同。每个例外必须绑定确切模块、依赖/API、兼容证据及验证结果；构件缺失和测试未运行不能写成平台不支持。
- JDK 8 无 `java.net.http.HttpClient`。当前 `ddd4j-data-external` 的 `GeoBaiduTemplate(HttpClient,String)`、`BaiduRegionTemplate(String,HttpClient)`、`BaiduRegionTemplate(String,HttpClient,RegionCache)`、`PconlineRegionTemplate(HttpClient)`、`PconlineRegionTemplate(HttpClient,RegionCache)`、`WeatherTemplate(HttpClient)`，以及 `ddd4j-data-crypto` 的 `FlksecCryptoStrategy(ObjectMapper,HttpClient,String,String)`，共七个构造器按完整 JVM 签名、源码符号和三线共同本地 HTTP 契约测试登记为平台专属公开入口；1.0.x 保留 Hutool HTTP 与本线公开构造器，2.0.x/3.0.x 保留 JDK HTTP 入口，各线使用各自的 Jackson 依赖。原始 API 差异必须继续输出；未登记的构造器及业务行为差异仍阻断验收。
- 加密 HTTP 契约测试的 Jackson `ObjectMapper` import 只在 3.0.x 随 Jackson 3 切换；1.0.x/2.0.x 保留 Jackson 2。检查器只允许该测试文件中列明的精确 import 替换，三线其余内容须逐字一致；测试断言或其他行为变更仍为 OPEN。
- Javalin 注册入口随各线框架版本变化：1.0.x 的 `Ddd4jJavalinWeb.configure(Javalin)` 与 2.0.x/3.0.x 的 `configure(JavalinConfig)` 是同一 Web 能力的两个精确平台签名。两项必须分别以当前源码声明、完整 JVM 签名和三线共同 HTTP 契约验证；原始双向 API 差异继续保留，签名或低线声明漂移立即恢复为未解决项。不得因此归并各线 Javalin 依赖、Maven 版本或启动 API。
- Quarkus 1.x 的 RESTEasy `ContainerRequestFilter.filter` 与独立 `Ddd4jQuarkusWebResponseFilter.filter`，在高两线改由 Quarkus REST 的 `@ServerRequestFilter`、`@ServerResponseFilter` 入口承载相同请求和响应完成逻辑。低线两个 `filter` 方法和独立响应过滤器的显式公开无参构造器均按完整 JVM 签名、源码声明及三线 HTTP 契约精确登记；构造器只供 Quarkus 1.x RESTEasy Provider 创建，高线仍使用 Quarkus REST 注解入口。三线保留各自 Quarkus/JAX-RS 依赖与命名空间。
- Javalin Shiro/Sa-Token 样例中的小型响应 Map 遵守共同值契约：拒绝 null 键/值、拒绝重复键、返回不可变 Map。1.0.x 缺少 `Map.of`，保留两个 `Java8Maps` 辅助类并在运行时拒绝奇数参数；2.0.x/3.0.x 使用各自 JDK 标准库 `Map.of`，奇数参数在调用处由编译器拒绝。只将低线 Shiro 样例公开 `Java8Maps.of(Object[])` 的精确签名登记为平台 API；其余源码差异继续逐项核查，不归并三线依赖。
- Micronaut 3 的手动 `Ddd4jMicronautContext.set/clear/openContext` 与 `HttpFilter.doFilter`，和 Micronaut 4 的 `ThreadPropagatedContextElement.updateThreadContext/restoreThreadContext`、`@RequestFilter.filter`，只按七个显式源码方法及完整 JVM 签名登记平台入口差异。三线上下文生命周期测试和 HTTP/OTel 契约证明对应行为；高线两个编译器 bridge 方法仅在完整 JVM 签名、关联显式方法及两线 `ACC_BRIDGE, ACC_SYNTHETIC` 标记均匹配时登记，任一漂移即恢复未解决状态。低线 Quarkus 响应过滤器构造器另以显式声明和精确签名登记；原始差异继续输出，其他源码/行为差异不获豁免。
- Micronaut 三线采集远端地址时，缺失或 `InetSocketAddress` 未解析均写入共同的 `unknown` 客户端地址，不因高线 `getHostString()` 有值而把未解析主机名当 IP；已解析地址仍使用数值地址。
- Micronaut 3/4 过滤器入口依框架版本保持不同；下游在创建 Publisher 时同步抛出运行时异常，三线必须原样传播异常、标记 HTTP 500 并结束 OTel Span、关闭调用线程 Scope 和失败的幂等 Scope。正常响应、异步错误和取消路径继续接受各线的真实 Web 契约验证，不因同步失败测试通过而自动豁免其余 Micronaut API 差异。
- 第三方组件版本由其真实发布与 JDK/框架兼容性决定，不从 DDD4J 分支号推导。EasyExcel 三线统一使用 `io.github.easy4j` 正式版 `5.0.1`；显式版本配置仍严格校验，构件缺失、字节码不兼容、组件覆盖缺失和漏洞门禁失败继续阻断验收。其他组件保持已有版本，必要的上游修复只覆盖实际阻塞的坐标，不改变其他组件发布范围。
- `*-spring-boot-starter` 按 Spring Boot 版本线管理，不建立组件式三线分支：Boot 2.3.x/2.7.x 的 starter 依赖 Easy4J 1.0.x，Boot 3.0.x—3.5.x 依赖 2.0.x，Boot 4.0.x/4.1.x 依赖 3.0.x；starter 自身快照版本为相应 Boot 线的 `.20260630-SNAPSHOT`。待发布版本将快照后缀去掉并按相应 Boot 线建立 tag；本轮本地验证不创建 tag 或发布构件。
- 三线自有公共 API 以兼容并集为准，保留构造器重载、Lombok/编译器生成的可观察成员、组件访问器与 JavaBean getter、值相等、异常和 JSON 契约。所有受 Git 管理的 Java 源及文档示例继续禁止声明 `record` 类型。

### 场景与验收

- Core：构造/null/空集合、值/JSON、错误码、CQRS 路由、Saga 逆序补偿、UnitOfWork 终态与异常、ES 乐观版本/回放/失败保留、投影位点与重置。
- Core 就绪状态：`ReadinessResult` 的诊断 Map 和 `ReadinessReport` 的结果 List 是不可修改的输入快照；诊断 Map 保留调用方的迭代顺序，使 `ReadinessResult.toString()` 的诊断字段在三线稳定。null Contributor 可跳过，Contributor 返回 null 或抛异常时聚合结果必须为未就绪且只暴露安全原因。
- Core 认证事件：`InMemorySubject.login` 成功时只发布一个 `AuthSucceededEvent`，其中保留当前请求、主体、令牌和发生时间；非法请求抛出原异常并只发布一个包含请求与安全原因的 `AuthFailedEvent`，主体保持匿名。三线通过相同的运行时契约测试验证，其他认证实现分别验收。
- UnitOfWork 每次 begin 创建独立回调边界；内层失败回滚后，外层捕获该失败仍可提交自身边界。工作失败时，单个回滚回调失败不得阻断后续回调或替换原始异常；该回调协调器不隐式创建数据库事务。
- Web：API 版本解析与分派、认证失败与匿名路径、线程及异步上下文传播/清理，以及第一批 W1/W2 的错误码和拦截器契约。
- Web 幂等租约：`IdempotencyLease` 的 key、ownerToken 与 TTL 值在三线保持一致；默认 `JsonKit` 将 TTL 写成 ISO-8601 Duration 字符串（例如 `PT30S`）并能往返。JDK 8/17 的 Jackson 2 适配可与 JDK 21 的 Jackson 3 实现不同；租约所有者隔离和完成/释放语义仍由 Guard 场景测试验收。
- Web 请求与响应值对象：`WebRequestData` 的请求元数据、`WebRequestFailure` 的方法/路径/空原因、`WebError` 的状态/编码/消息/数据，以及 `ReadinessResponse` 的布尔状态，在三线各自 Jackson 运行时下保持相同 JSON 往返语义；公开构造器、组件访问器和 JavaBean getter 不变。
- WebMVC 拦截器次序：启用 API 版本解析时，注册优先级严格为版本解析先于请求上下文，随后语言切换；即使配置器以相反顺序向 Spring 注册，也不能让上下文先读取到空版本。关闭版本解析时保持既有上下文/语言相对顺序。三线以各自 Spring WebMVC 的实际 `InterceptorRegistry` 排序验证。
- Web 请求上下文版本 JSON：`WebRequestContext` 的九参构造器保留既有八参兼容入口；非空 `apiVersion` 在三线 JSON 中以规范化 `major.minor.patch` 字符串表示并可往返，null 版本仍保持 null。`ApiVersion` 的解析和数值比较规则不变。
- Web Bearer 认证结果：`BearerSubjectAuthenticator.Authentication` 在三线保留相同构造器、组件访问器、JavaBean getter、值相等/hashCode/toString 契约；仅有 token、principal/subject 为 null 的结果经各线 `JsonKit` 序列化后可恢复。更复杂的主体 JSON 形态另行验收。
- Web Validation：JDK 8 使用 `javax.validation`/Validation 2；JDK 17 和 21 使用 `jakarta.validation`/Validation 3。`AllowableValues`、`NumberValue`、`PhoneNumber`、`StringDateValue` 四个自定义约束必须由各线实际 Validation Provider 识别并执行，不能仅以注解和验证器编译通过代替行为验证。两种命名空间的公开类型只按已核实的精确类型映射比较。
- MQ：因果头传播、Broker 初始化/关闭/失败恢复、至少一次确认、Outbox 并发租约/重试/条件确认，保留旧 `TransactionalOutboxPublisher` 的兼容入口。
- 订单 JDBC Outbox：持久化和重新领取消息必须保留冻结的 correlationId/causationId 原生头；旧表升级后历史记录使用空头默认值，消息 ID 仍由 Outbox 记录补齐。
- Kafka 消费者初始化与 `close()` 并发时，关闭不得先于正在进行的初始化完成而返回；已创建的消费者和执行器最终只释放一次，关闭后不得再登记可用资源。各线保留自身 Kafka SDK 与 JDK 适配。
- Artemis JMS 连接已创建但 `start()` 失败时，初始化须向调用方保留原始启动异常，并立即关闭该连接；之后的客户端 `close()` 不得重复关闭它。JDK 8 使用本线 `javax.jms`，JDK 17/21 使用本线 `jakarta.jms`，三线验证相同资源归属行为。
- NATS JetStream：无原生 tag selector 时，带 tag 与无 tag 消息都必须到达同一逻辑 topic 的监听器，再按 `TagMatcher` 表达式过滤；原生消息头传递 tag 和稳定/因果 ID，拒绝的 tag 不调用业务监听器。三线以各自 JDK/客户端依赖和真实 NATS Broker 用例验证。
- 样例：订单创建→加商品→支付→事件/Outbox→读模型→查询，以及重复请求、事务回滚、发送失败和资源关闭。
- Javalin Sa-Token/Shiro 的 `CreateGoodsRequest` 与 `AddOrderLineRequest` 保留 1.0.x 公开类可继承的兼容入口；相同字段的子类与基类双向相等且哈希一致；子类覆写 JavaBean getter 时，`equals/hashCode` 按 getter 的可观察值判断，三线各自 JSON Mapper 可往返。2.0.x/3.0.x 可保留各自 Jackson 注解，但不能以 `final` 或不同的 `equals/canEqual` 语义改变这一共同契约。
- Javalin Sa-Token/Shiro 的 `CreateOrderRequest` 与 `UpdateGoodsRequest` 同样保留可继承入口、双向 `canEqual` 值相等及已有哈希结果；当子类覆写 JavaBean getter 时，`equals/hashCode` 按 getter 的可观察值判断。三线可分别使用 Lombok 或手写方法，不能让字段直读改变此契约。
- 每个共同场景有稳定 ID，映射三线实际测试、支持平台和证据。声明支持的平台必须在本线 JDK 下构建并通过相应行为验证；云服务和生产结果单列。
- 最终门禁核对源码/模块/公开 API/场景/文档并集、Easy4J 版本线、三线当前构件及输入指纹。必需项 FAIL、BLOCKED 或 NOT_RUN 时不能宣称产品收敛完成；历史报告不得替代本轮运行证据。
- 每周依赖审计：三条分支各自读取本线 BOM；明确的逐线固定版本必须精确匹配，明确排除的坐标保留原因，其余管理坐标与 Maven Central 最新稳定发布版比较（仅发布预览版时比较最新预览版），落后或查询失败均须报告并阻断版本检查。独立漏洞扫描须实际解析 Maven POM 包，并通过生成的 CycloneDX 清单覆盖本线 BOM 的全部直接管理版本；HIGH/CRITICAL 结果、空报告或清单覆盖不足均阻断验收。OFDRW 2.4.0 的 JDK 8 字节码兼容不等于 `ofdrw-full` 转换器与当前 PDFBox 3.0.7 兼容，当前三线只管理 `ofdrw-core`；完整转换能力须以应用集成测试单独验收。
