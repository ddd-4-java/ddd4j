# Task 1.8 Report — 07-cqrs-projection.md（ddd4j-core 已对齐）

## Status: DONE

## Deliverable

- 文件：`ddd4j/docs/reference/fuin-api-patterns/07-cqrs-projection.md`
- Commit：`a6945529`（feature/2.0.x，单文件，77 insertions）
- 行数：77（要求 60-100）
- 章节：6 个，标题逐字符合 brief（来源 / fuin 的设计 / 优点（值得借鉴的）/ 缺点（应规避的）/ ddd4j 自研决策 / 落地计划）；title 精确为 `# 07. fuin API 模式：CQRS 投影读侧`
- 引用密度：56 处 `.java:NN` file:line 引用（42 个去重后；06 篇为 49 处，达标）；1 个代码 snippet（JpaView 三方法）
- 未修改 01-06 与 README

## Section 5 结论（已逐条对源码核验）

- 已对齐（4 组）：位置三方法（ddd4j ProjectionService.java:19/:27/:36 ↔ fuin esc ProjectionService.java:35/:46/:56，含「缺省 0」语义）；cron＋chunkSize（默认同为 100）视图配置；ViewManager 生命周期 start/stop/triggerOnce ↔ createViews/shutdownViews/updateView；「先投影后推位置」顺序
- 改写／超出（4 条）：(a) 纯 Java 框架无关（ViewManager/ViewScheduler 双 SPI，fuin 写死 Spring 且 Quarkus 整份重抄）；(b) ProjectionRunner 循环沉淀为纯类＋EventChunkReader SPI 解耦存储；(c) 服务/实体/仓储三分离＋不可变位置值对象＋update 返回持久化实例；(d) TypedEventDispatcher 以 getEventClass() isInstance 校验分发
- 不借鉴（3 条）：Spring 专用调度器（阶段 7 按运行时各写）；TransactionTemplate＋EntityManagerFactory 硬接线；字符串 eventType 路由
- 借鉴（新增）：API 层无；Adler32 投影流指纹作为实现层微调写入落地计划第 3 条

## Sources read（全部逐行读完）

- fuin springboot view/ 全部 3 文件（QryProjectionService 56 行、QryProjectionPosition 87 行、SpringJpaViewManager 264 行）
- fuin core View.java（27 行）、JpaView.java（40 行）；esc ProjectionService.java（58 行）
- fuin quarkus view/ 目录确认（QuarkusJpaViewManager 190 行，io.quarkus.scheduler :7/:44/:71）；base/EventstoreConfig.java 确认投影侧模块论断
- ddd4j-core readmodel/ 全部 17 文件（16 类＋package-info）
- 兄弟篇 03（已对齐模板）、06（引用密度标准）；计划阶段 7 段落、ADR-0003 引用点

## Brief corrections（信源码不信 brief）

1. brief 称 SpringJpaViewManager「~216 行」——实际 264 行（wc -l 核验）
2. brief 称生命周期钩子含「ContextRefreshedEvent」——源码无此事件；实际为 SchedulingConfigurer.configureTasks（:101-104）＋ApplicationListener<ContextClosedEvent>（:21/:106-109），文档按源码写
3. brief 称「@Scheduled」——SpringJpaViewManager 未用 @Scheduled 注解，而是 CronTask＋taskRegistrar.addCronTask（:117-118）；Quarkus 版才用 Scheduler
4. brief 列 readmodel 16 类——目录实为 16 类＋package-info（17 文件），文档按「16 类＋package-info」表述

## Self-review

- [x] 6 节标题逐字正确、行数 77 在区间内、全角中文标点
- [x] 每条对照双侧 file:line；抽查 sed 验证：esc ProjectionService.java:35/:46/:56、SpringJpaViewManager.java:48-50/:117-118/:125-131/:172-181/:203-205、ddd4j ProjectionService.java:19/:27/:36 全部命中
- [x] 候选角度逐条验证后写入（未发现不成立项；(c) 的「签名对齐」以 esc 接口为准、QryProjectionService 为 Spring 实现分别引用）
- [x] 落地计划为微调型：阶段 7（Task 7.1 / 7.7-7.13 / 流 ID 指纹微调）＋ ADR-0003
- [x] 单 commit、规定提交信息、01-06 未动
