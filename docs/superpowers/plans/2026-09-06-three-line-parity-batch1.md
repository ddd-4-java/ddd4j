# 三版本共同契约第一批 Implementation Plan

> 执行方式：在当前会话逐项实施，TDD 与完成前验证；使用现有三个目录，不创建 worktree，不派生新分支。

**Goal:** 修复已确认的四值对象、Web、默认日志与 MQ 参数行为差异。

**Architecture:** 共同契约以 2.0.x 为初始对照，经真实反例修正。公开 API 取兼容并集，1.0 使用 Java 8 等价实现，2.0/3.0 保留 record 与原依赖线。

**Tech Stack:** JDK 8/17/21、Maven、JUnit、Spring、Guice、CodeGraph。

**Spec:** `../specs/2026-09-06-three-line-parity.md`

## 全局约束

只允许 JDK 语法、Maven 依赖、Jackson、sample/Quarkus 差异；禁止非豁免结构和接口漂移。保留旧公开 API；本批不改变 EventStore。每项使用实际源码的失败与通过证据，不以名称存在代替行为验证。

## Task 1：四值对象

Files：规格 V1 列出的四个相同相对路径，在三个目录实施；测试工具 `verification/three-line-parity/ValueContractProbe.java` 与 `scripts/verify-three-line-value-parity.py` 同路径同步。

Consumes：四类既有构造器与 getter。Produces：所有字段的组件访问器、bean getter、值 equals/hashCode/toString，QLExpressValidationResult 公开 `(boolean valid, String message)` 构造器。

- [x] 写探针：反射比较完整公开构造器/方法表；同字段两个实例进入 HashSet 后 size=1；Redis nativeMessage 不同须 size=2；null 字段不抛异常；输出三版本签名和值比较结果。
- [x] 运行 `python3 scripts/verify-three-line-value-parity.py --roots . ../ddd4j-v2.0.x ../ddd4j-v3.0.x --output /tmp/ddd4j-parity-batch1-red-values`，确认针对已知反例失败。
- [x] 在 1.0 补组件访问器和值行为，在 2/3 补 bean getter；保持所有已公开方法。
- [x] 同命令换 green 输出目录，确认三版本实际编译/运行与 API/值比较通过。

## Task 2：Web

Files：三个目录中 `ddd4j-web/ddd4j-web-webmvc/src/test/java/io/ddd4j/web/webmvc/ThreeLineWebParityTest.java`；生产 BaseWebConfig、GlobalExceptionHandler、FeignHeaderInterceptor 原路径。

- [x] 写真实 InterceptorRegistry 测试，子类暴露注册列表；非空输入必须注册同一对象且匹配原路径。写 MissingRequestHeaderException 两个国际化分支与 Feign 三种上下文值测试。
- [x] JDK8 Maven 目标模块测试确认错误条件/空串失败；请求头枚举只做符号语义统一，响应测试为原有行为保护，不接受 test skipped。
- [x] 修复条件为 `Objects.nonNull(baseWebInterceptors) && !baseWebInterceptors.isEmpty()`；请求头两分支统一 HEADER code；SYSTEM_ID 使用 StringUtils.hasLength 决定默认值。
- [x] 运行新用例与现有 WebMVC 回归。

## Task 3：默认日志

Files：1.0 缺失 `ddd4j-data/ddd4j-data-logs/src/main/java/io/ddd4j/data/logs/DefaultApiOperationLogProvider.java`，三个版本 `ddd4j-runtime/ddd4j-runtime-guice/src/test/java/io/ddd4j/guice/ThreeLineLogsParityTest.java`，Ddd4jLogsGuiceModule。

- [x] 验证 Guice 返回 provider 在成功/失败回调产生 ACCESS_MARKER 日志；用内存日志收集器观察真实回调。
- [x] 确认 1.0 空 provider 不满足测试。
- [x] 恢复现有 2.0 实现到同路径并做 javax 适配，恢复 Guice 绑定，确认依赖无循环。
- [x] 运行日志用例与 Guice 回归。

## Task 4：MQ 空白参数

Files：OnsMQClient、TdmqMQClient 原路径；各模块同路径 `ConsumerGroupParityTest.java`。

- [x] ONS 测试空白 group/topic 在 Broker 建连前拒绝；TDMQ 注入接收参数的 Subscriber，断言空白监听器 group 使用配置值。
- [x] 确认 1.0 分支错误的 isNotEmpty 不能满足测试。
- [x] 仅将这些业务校验替换为 StrKit.hasText。
- [x] 运行目标测试与已有 acknowledgment/header 回归。

## Task 5：交付验证

- [x] 全部用例记录真实命令、JDK、测试数量、RED/GREEN 输出位置。
- [x] `git diff --check`、CodeGraph 增量刷新及新差异清单；检查原始非任务修改未受影响。
- [x] 更新任务状态，未验证项保留未完成；不进行 commit/push。

实施结果与例外：[第一批验证记录](2026-09-06-three-line-parity-batch1-results.md)。后续批次仍按共同规格推进，不因本批勾选而视为全仓一致。
