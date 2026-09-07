# 三版本一致性第一批实施与验证记录

## 结果范围

第一批完成：四值对象公开接口/值语义、非空 Web 拦截器注册、默认日志实现恢复、Feign 空系统 ID、ONS/TDMQ 空白消费配置。非本批 EventStore、其他缺失对象、剩余 record/集合/字符编码差异未宣称完成。

规格事实源：`../specs/2026-09-06-three-line-parity.md`。实施计划：`2026-09-06-three-line-parity-batch1.md`。

## 代码变更

- 三版本 ProjectionRunInfo、原生 MyBatis SqlObservation、RedisStreamRecord、QLExpressValidationResult 保留原公开 API，并统一组件访问器和 bean getter；Java8 实现补齐值相等、hashCode、record 风格 toString，校验结果构造器公开，Redis 的 nativeMessage 参与值比较。
- 1.0 BaseWebConfig 非空拦截器列表进入注册分支；通过真实 InterceptorRegistry/MockHttpServletRequest 检查多拦截器、包含和排除路径。
- 1.0 恢复 DefaultApiOperationLogProvider 同名源码与 Guice 默认绑定。通过实际日志后端验证成功 ACCESS_MARKER 日志和异常日志。
- Feign 自动填充使用 StringUtils.hasLength，null/空字符串系统 ID 统一为 "0"，非空值保留。测试使用 ThreadContext.put 构造实际空字符串，避免 set 忽略空串造成假绿。
- ONS hasText 校验在客户端创建前拒绝空白 group/topic；TDMQ 空白监听器 group 回退 defaultGroup。
- HEADER/PARAM 枚举选择按命名语义统一：两者数字代码同为 400，且消息显式传入，故不是响应数字的行为修复。两个国际化分支保留 400 和原消息。

## 验证前置修复

- 三版本新增 parity-verification Maven profile，明确 skip=false/skipTests=false；1.0 原始配置即使传 -DskipTests=false 仍跳过，不接受这种 BUILD SUCCESS 为测试证据。
- 1.0 ddd4j-dependencies 在本层优先导入既定 Spring 5.3.39 BOM。原测试类路径：core 5.3.23、web/test 5.3.25、webmvc 5.3.39，原 Web 契约测试报 PathPatternParser.initFullPathPattern NoSuchMethodError。最终 XML classpath 中 14 个 Spring Framework jar 均为 5.3.39。
- 三版本 Guice 模块显式声明对应 javax/jakarta Servlet provided 依赖，保证默认日志依赖可见；测试使用 JDK8 可运行的 logback 1.3.14（test scope），未改变生产日志后端。
- 三版本 .gitignore 精确反忽略 data-logs 的 Java logs 包，避免新增实现被通用 logs/ 规则隐藏；该文件已作为 untracked 源码正常显示。

## RED 证据

| 门禁 | 实际结果 | 原始日志 |
|---|---|---|
| 四值对象原始契约 | JDK8/17/21 全部编译成功，运行分别 16/14/14 项失败 | /tmp/ddd4j-parity-batch1-red-values/results.json |
| Web | 4 tests，2 failures，0 errors，0 skipped：非空拦截器未注册、空系统 ID header 缺失 | /tmp/ddd4j-parity-batch1-red-web-final.log |
| Guice 默认日志 | 1 test，1 failure：成功回调没有日志 | /tmp/ddd4j-parity-batch1-red-logs.log |
| ONS | 2 tests，2 failures：空白配置到达禁止的 Broker 创建入口 | /tmp/ddd4j-parity-batch1-red-logs-mq.log |
| TDMQ | 1 test，1 failure：空白 group 未回退 | /tmp/ddd4j-parity-batch1-red-logs-mq.log |

先前试跑中的测试夹具错误（Mockito 缺依赖、空 header 导致测试 NPE）未当作有效 RED；表中使用修正夹具后的业务失败。

## 最终 GREEN 证据

| 版本 | JDK | 测试类 | 用例 | Failures / Errors / Skipped |
|---|---|---:|---:|---|
| 1.0.x | Corretto 8u504 | 19 | 96 | 0 / 0 / 0 |
| 2.0.x | Corretto 17.0.20.1 | 19 | 98 | 0 / 0 / 0 |
| 3.0.x | Microsoft 21.0.12.1 | 19 | 98 | 0 / 0 / 0 |

日志分别为 `/tmp/ddd4j-parity-batch1-final-1.log`、`final-2.log`、`final-3.log`（后两者同此前缀）。三次 Maven exit 0 / BUILD SUCCESS。计数来自带测试类名的结果行，未重复累加模块汇总行。

收尾时另一个进程对 1.0 执行 reset 到 HEAD，撤销未提交修复并清除了新增文件。本会话已从审查过的本批内容恢复这些修改，保存独立恢复补丁 `/tmp/ddd4j-parity-batch1-recovery.apply-patch.txt`，并重跑 1.0 回归。最新 1.0 证据为 `/tmp/ddd4j-parity-batch1-recovered-1.log`：exit 0 / BUILD SUCCESS；不能仅使用 reset 前记录证明恢复后的状态。三个版本值对象门禁亦重新运行并增加编译前后源码哈希稳定性检查。

相同值对象探针使用三个版本原始源码独立编译，逐字段不同、null、异类、HashSet 去重、访问器值、完整公开构造器参数名及公开方法表、hash/text 跨版本比较均通过，exit 0。证据：`/tmp/ddd4j-parity-batch1-final-values/results.json`。

复算命令（仓库根运行，按版本设置 JAVA_HOME）：

```sh
python3 scripts/verify-three-line-value-parity.py --roots ../ddd4j ../ddd4j-v2.0.x ../ddd4j-v3.0.x --output /tmp/ddd4j-parity-values-rerun

mvn -o -B -ntp -Pparity-verification \
  -pl ddd4j-runtime/ddd4j-runtime-guice,ddd4j-mq/ddd4j-mq-ons,ddd4j-mq/ddd4j-mq-tdmq -am \
  '-Dtest=ThreeLine*Test,ConsumerGroupParityTest,Ddd4jWebMvc*Test,Guice*Test,Ddd4jGuiceModuleTest,Ons*Test,Tdmq*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false -Denforcer.skip=true test
```

1/2 使用 Maven 3.9.16；3 使用 `./mvnw` Maven 4.0.0-rc-6 并去掉 `-o`（离线解析触发内部异常，联网目标构建通过）。2/3 的既有 Mockito inline 测试需要可自附加 JVM 的执行环境，本次在沙箱外运行；新日志测试只用 JDK Proxy，不依赖自附加。

这些命令使用 `-Denforcer.skip=true`，不代表无豁免发布门禁通过；未跑全量 reactor、数据库容器全矩阵或生产部署。

## 独立审查与处置

独立只读审查未发现本批 P0/P1 运行逻辑缺陷；两项验收问题均处理：

1. 新日志实现被忽略：已加入精确反忽略，并验证 git status 可见。
2. 值对象只测正例不足：补逐字段反例、双向不等、null/异类和访问器真实输入断言，三 JDK 重跑通过。

另外补了多拦截器与排除路径行为测试。恢复的日志代码沿用原实现的 `ServletRequest && ServletResponse` 参数筛选；此为既有独立问题，未在本批顺带重构，纳入后续。

## Git 与交付边界

本会话没有执行 git commit/push。实施期间其他进程创建了 1.0.x 提交 `6c12b323`（标题为 coverage profile），其中包含本批早期四值对象、规格、计划、Web 测试初稿及探针。该提交保持不动；其新增 coverage profile 不是本会话修改。

另观察到其他进程的 deploy.yml 修正提交 `0a1b9883`；本会话未修改该部署文件或执行任何 reset/rebase/pull。由于存在并发 Git 操作，最终交付应核对当前 HEAD 和源文件哈希，不能仅凭 commit 标题归属变更。

因此审查整批时，1.0 应比较起始 `6ee5879c` 到当前工作树，同时单独识别外部 coverage profile；2.0 起始 `ebef4ed9`，3.0 起始 `1cce1a91`。其余本批改动仍为未提交修改/新增文件。

## 后续

下一批按照规格推进 EventStore：先固定同步/异步对象拓扑与签名，再验证事件时间/版本副作用、未知字段、分页、冲突与事务。四对象通过不能推导全仓 record 或所有逻辑已经一致。
