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
- `ddd4j-extensions/ddd4j-extension-qlexpress/src/main/java/io/ddd4j/extension/qlexpress/model/QLExpressValidationResult.java`

所有构造参数与顺序以现有 record 组件为准，公开构造器一致；同时保留 bean getter 与组件访问器。相同全部字段应值相等，hashCode 一致；不同字段应不等。RedisStreamRecord 的 nativeMessage 必须参与 equals/hashCode。引用字段为 null 时 equals/hashCode 不得抛异常。toString 采用现有 record 格式，业务字段全部列出。集合字段本批不改变现有快照/引用语义。

### W1：Web 拦截器

非空 BaseWebInterceptor 列表全部注册，保持每项 pathPatterns/excludePathPatterns；空列表和 null 列表不注册，不抛异常。

### W2：请求头异常与 Feign 默认值

MissingRequestHeaderException 在国际化开启/关闭两条路径均返回 SC_MISSING_REQUEST_HEADER；不能直接沿用任一分支非国际化路径的旧错误码。

开启 autoFillSystemId 且无 Web 请求覆盖值时，ThreadContext 中 null/空串统一写入三个系统 ID header 为 "0"；非空值原样保留。空白串策略本批沿用已有 2.0/3.0 的 hasLength 语义，不擅自扩展到 trim。

### L1：默认操作日志

三个版本提供相同 DefaultApiOperationLogProvider 对象和方法；Guice 默认绑定真实实现。成功/失败回调进入 doApiOperationLog，保留 saveLog 扩展点及 Hidden 处理。1.0 只做 javax 等依赖适配，禁止用空接口实例代替。恢复实现不得建立新的循环依赖。

### M1：MQ 空白参数

ONS consumer group/topic 使用 hasText：空白监听器值回退配置值，最终仍为空白时在创建 Broker 连接之前拒绝。TDMQ 空白监听器 group 回退 defaultGroup；可通过注入 BrokerSubscriber 检验，不连接生产 Broker。

## 后续批次（不在本批完成声明范围）

第二批：EventStore 同步/异步对象拓扑、四方法签名、事件时间与版本副作用、payload 未知字段、分页与事务边界。第三批：非豁免缺失对象、外部 HTTP 参数以及框架上下文传播；禁止直接删除 HttpClient 参数以实现 JDK8 适配。第四批：剩余 record、集合 null/复制/可变性、字符编码与规则返回值。第五批：全目录/API/行为差分门禁。

## 验证标准

每个修复先有真实失败证据，再修改实现并复测；测试与目标源码按版本匹配编译，禁止复用旧 target 类冒充新源码。验收记录明确区分值对象隔离测试、目标模块 JUnit、受影响模块回归、全量 reactor。没有执行的门禁不得标通过。

## D1：三线依赖组件清单与 JDK 基线（2026-09-08 用户批准）

状态：实施中，严格门禁尚未通过；结果见 `../plans/2026-09-08-dependency-alignment-results.md`。

- 以三个 `ddd4j-dependencies/pom.xml` 显式维护的组件并集建立清单。低 JDK 线已有能力须在高 JDK 线保留，坐标迁移须记录替代关系。
- 使用 Maven effective POM 判定 BOM 覆盖，不以文本中没有直接声明判定缺失；上游 BOM 间接导出的历史组件单独统计，不机械复制已废弃模块。
- 版本按 JDK 8/17/21 及 Spring 5/6/7、Jackson 2/2/3 兼容组合确定。普通库无兼容原因时保持同版；不凭版本号大小推定 JDK 兼容。
- 没有适用发行版的组件必须给出具体 JDK/生态限制，不添加不可用版本冒充对齐。旧坐标改名、模块合并和 BOM 管理须显式说明。
- 验证至少包括：清单覆盖、属性解析、重复坐标、产物可解析性、JDK 字节码基线，以及受影响模块的真实回归。多版本 JAR 的高版本目录不作为基础 JDK 违规；可选高版本实现需逐项核查。
- 保留当前 checkout、未提交修改及各线 Maven 模型；本次不提交、推送、发布，不初始化新规格体系。
