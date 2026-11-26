# Shared sample contracts

这是供 S01–S07 平台测试复用的小型订单测试包。已有 `OrderScenario`、订单领域、应用服务、同步读模型和本地 Outbox 保持原行为。四类场景通过三个驱动接口和 `SampleContractAssertions` 提供固定输入与断言，不负责平台启动、调度或测试发现。

| 场景 | 入口 | 驱动 | 行为 |
| --- | --- | --- | --- |
| default | `defaultOrder(driver, uniqueOrderNo)` | `OrderSampleDriver` | 保存草稿、明细更新、支付、读回及买家/状态过滤 |
| cqrs | `cqrsOrder(driver, uniqueOrderNo)` | `CqrsOrderSampleDriver` | 命令持久化、三个真实共享订单事件、独立投影、故障不推进源位置、重试恢复、空重试无重复查询行 |
| satoken | `authentication("satoken", ...)` | `AuthSampleDriver` | 登录、认证主体、允许资源、拒绝访问、异常清理、退出令牌失效 |
| shiro | `authentication("shiro", ...)` | `AuthSampleDriver` | 与上行相同的可观察行为，通过真实 Shiro 入口验证 |

订单输入使用已有 `CreateOrderCommand`/`AddOrderLineCommand`：买家 `buyer-1` / `Alice`，商品 `goods-1` / `DDD Book`，数量 2，单价 59.90；预期总额 119.80。每次传唯一订单号，测试存储需隔离或清理。认证账号、密码、预期主体、允许/拒绝权限和资源均由调用方按原平台测试数据传入。

平台驱动必须通过实际 HTTP/容器入口执行命令和查询，保留原路由、序列化、认证请求字段和令牌格式。驱动将真实响应解码为共享 DTO，不能把固定值或自报成功标记当成业务结果。启动、关闭和容器装配在平台原生测试中完成。

默认场景的调用方在无 bus、ES、后台投影、MQ 连接和 auth 的装配下创建驱动。这里的本地夹具仅证明共享业务不需要调用上述服务；平台可选依赖及启动排除由各平台单独验证。同步 `OrderReadModelPort.project` 和本地 Outbox 是已有默认能力。

CQRS 测试需隔离投影流并暂停后台调度，使 `project()` 执行并等待实际投影批次。`persisted` 直接读写仓储，`events` 读取真实持久事件，`projection` 直接读独立查询仓储，`position` 读取真实持久检查点。`find` 不得使用写模型回退。故障注入发生于投影处理器写入前，原始业务失败转换为 `IllegalStateException`；失败后源位置和查询行均不能提前变化。位置是源游标，允许有空洞，不能用本次事件数量推导。

认证驱动将实际未认证/拒绝响应转换为 `SecurityException`，将已注入的业务异常转换为 `IllegalStateException`。`authorizedResource` 返回实际受保护业务内容；`contextPrincipal` 必须观察执行线程或原生作用域，可用原生测试钩子跨线程取回观察值，不添加产品调试接口。驱动不得替平台执行清理，也不得固定返回空；框架拦截器/finally 应负责清理。预期主体可为用户 ID，无须等于登录用户名。

`SampleContractAssertionsTest` 是明确受控的断言验证夹具。default 直接复用 `OrderScenario`；CQRS 使用真实共享 `ProjectionRunner`、`DefaultProjectionService` 和位置仓储，消费共享 Outbox 失效事件后加载写模型并写独立查询仓储；认证用可故意破坏的会话/上下文 double 检测错误，不运行 Sa-Token/Shiro 引擎。任何共享夹具测试通过都不能作为 HTTP、容器、真实数据库或原生安全框架验收。

证据沿用 JUnit XML，不增加报告框架。平台任务记录一行：

| task/scenario | module/GAV | source SHA-256 manifest | JDK/wrapper | command/exit | tests/failures/errors/skips | startup/HTTP/persistence/failure/retry/cleanup/close evidence | acceptance layer |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Sxx/scenario | 实际模块及坐标 | 清单路径及哈希 | 实际版本 | 完整命令及退出码 | 当前 XML 计数 | 日志/请求/查询/关闭记录，未执行需明确 | shared fixture / actual platform / target service |

共享夹具证据和平台验收分行记录，跳过不记通过。测试执行可从仓库根目录使用当前 JDK17 与 `./mvnw -pl ddd4j-samples/ddd4j-sample-order-testkit -am verify`，不跳过测试。后续真实平台验证使用对应平台 reactor 及启动命令。

`EnvelopeOrderHttpDriver` 仅适配既有 `R.data` 包装、原订单路径/认证头/支付头以及 Money 或金额响应；与 `HttpOrderSampleDriver` 一样只负责真实 HTTP 传输和解码，不启动平台、不执行领域逻辑、不清理认证上下文。
