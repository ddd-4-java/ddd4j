# ddd4j-extension-monitor

ddd4j 框架的**监控告警**工具库：钉钉 / 企业微信 / 飞书 群机器人 + 启动期应用通告。

> **v2.x 重构**：从"DDD 分层 + Logback 强绑定"重构为"按技术能力切分 + 0 日志框架绑定"。
> 本文同时给出**迁移指南**（v1 → v2）与使用文档。

---

## 1. 特性

- ✅ **零日志框架绑定**：仅依赖 SLF4J `slf4j-api`，运行时使用什么日志实现（Logback / Log4j / JUL）由业务方决定。
- ✅ **纯 Java 工具库**：不依赖 Spring / Quarkus / Javalin 任何容器框架，上层框架完成装配。
- ✅ **模块化通道**：钉钉 / 企业微信两条独立通道，互不污染。
- ✅ **启动期通告**：读取 `git.properties` 自动广播版本号给机器人。

---

## 2. 依赖

| 依赖 | 必选 | 说明 |
| --- | :-: | --- |
| `slf4j-api` | ✅ | SLF4J 接口 |
| `ddd4j-core` | ✅ | 框架核心 |
| `ddd4j-kit` | ✅ | 工具集（`StrKit`、`JsonKit`、`IpKit`） |
| `assertj-core`（test） | ⚪ | 测试断言 |

> v2.x **不再需要** `logback-classic` / `janino` / `guava`。
> 旧 v1 的 Logback Appender / 自带限流（基于 Guava `RateLimiter`）已下线。

---

## 3. 使用

### 3.1 直接使用门面（推荐）

```java
import io.ddd4j.extension.monitor.core.Monitor;
import io.ddd4j.extension.monitor.core.Sender;
import io.ddd4j.extension.monitor.channel.dingtalk.DingTalkRobotSender;
import io.ddd4j.extension.monitor.channel.feishu.FeishuRobotSender;
import io.ddd4j.extension.monitor.channel.wecom.WeComRobotSender;

// 钉钉
DingTalkRobotSender sender = Monitor.ofDingTalk("access_token", "secret");
sender.send("Hello, DingTalk");
sender.sendMarkdown("标题", "正文", List.of("13800001111"));

// 企业微信
WeComRobotSender wecom = Monitor.ofWeCom("webhook_key");
wecom.send("Hello, WeCom");

// 飞书（v2.x 新增）
FeishuRobotSender feishu = Monitor.ofFeishu(
        "https://open.feishu.cn/open-apis/bot/v2/hook/xxx",
        "SECxxx");   // 加签密钥，无则置 ""
feishu.send("Hello, Feishu");

// ✨ 业务侧不用关心 channel 差异 —— Sender 默认方法统一构造 markdown 消息
Sender any = wecom;                                       // 任一 Sender 实现
any.sendMarkdown("系统告警", "**xxxi** 服务异常");        // 默认方法，自动序列化 msgtype=markdown
```

### 3.2 应用启动通告

```java
DingTalkRobotSender sender = Monitor.ofDingTalk("access_token", "secret");
Monitor.startupReporter(sender, "my-app").init();
```

要求 classpath 下存在 `git.properties`（由 `maven-git-commit-id-plugin` 生成）。
缺失时不会抛异常，仅跳过通知。

### 3.3 基于 Properties 配置（与 Spring / Quarkus 集成）

```yaml
ddd4j:
  monitor:
    enable: true
    rateLimiterPermitsPerSecond: 0.5
    dingtalk:
      enable: true
      token: 钉钉机器人access_token
      secret: 钉钉机器人加签密钥
    wecom:
      enable: true
      key: 企业微信机器人webhook_key
    feishu:
      enable: true
      webhookUrl: https://open.feishu.cn/open-apis/bot/v2/hook/xxx
      secret: 飞书机器人加签密钥（"不勾选签名校验"时留空）
    app:
      project: my-project
      env: prod
      name: my-app
```

```java
// 装配层（Spring / Quarkus / Javalin 各自的模块）
BaseMonitorProperties props = ...;        // @ConfigurationProperties(prefix = "ddd4j.monitor")
BaseMonitorConfig config = new BaseMonitorConfig();
DingTalkRobotSender sender = config.dingTalkRobotSender(props);
config.applicationStartReporter(sender, props.getLog().getApp().getName()).init();
```

### 3.4 健康检查端点

本版本未提供统一健康检查端点（移除了原 `HealthEndpoint` 类）—— 由上层 Web 框架（Spring Actuator / Quarkus Health / 自实现）按需暴露。

---

## 4. 目录结构

```
io.ddd4j.extension.monitor
├── Monitor.java         # 门面入口：ofDingTalk / ofWeCom / ofFeishu / startupReporter
├── Sender.java          # 通道 SPI（send、sendMarkdown、renderMessage）
├── channel/             # 各消息通道
│   ├── dingtalk/         # 钉钉（was dingding）
│   │   ├── DingTalkClient.java
│   │   ├── DingTalkProperties.java
│   │   └── DingTalkRobotSender.java
│   ├── wecom/            # 企业微信（was qiwei）
│   │   ├── WeComClient.java
│   │   ├── WeComProperties.java
│   │   └── WeComRobotSender.java
│   └── feishu/           # 飞书（v2.x 新增）
│       ├── FeishuClient.java
│       ├── FeishuProperties.java
│       └── FeishuRobotSender.java
├── message/             # 通用协议 DTO
│   ├── At.java
│   ├── CodeVersion.java
│   ├── Markdown.java
│   ├── Message.java      # 顶层 at/text/markdown 包装
│   └── Text.java
├── runtime/
│   └── ApplicationStartReporter.java
├── config/
│   ├── BaseMonitorConfig.java
│   └── BaseMonitorProperties.java
└── （util/IpUtils 已删除，改用 io.ddd4j.kit.web.IpKit；HealthEndpoint 也已下线）
```

### 4.1 Markdown 消息协议层字段说明

`message/Markdown.java` 同时持有 `title` / `text` / `content` 三个字段，是为了**兼容钉钉 / 企业微信 / 飞书 三端的协议差**：

| 字段 | 钉钉 markdown | 企业微信 markdown | 飞书 post（v2.x 默认） |
| --- | --- | --- | --- |
| `markdown.title` | ✅ 第一行标题 | ❌ 不使用 | ✅ `content.zh_cn.title` |
| `markdown.text` | ✅ 正文 | ❌ 协议用 `content` | ✅ `content.zh_cn.content` 段落 |
| `markdown.content` | ❌ 不使用 | ✅ 实际渲染正文 | ❌ 飞书用 `text` 嵌套段落 |

`Message.markdown(title, body, atMobiles)` 工厂方法会**同时填充 `text` 和 `content`**，
所以同一份 `Message` 既能正确生成钉钉的 webhook 负载、也能正确生成企微的负载。
各通道的 `Sender.sendMarkdown(...)` 拿到这个 `Message` 序列化为 JSON 后，
序列化输出对各端都有效（多余的字段被忽略，缺失的字段被填充）。

**与 v1.x 的兼容**：v1.x 的 `MarkDownVO` 同时有 `text` / `content` 两个字段，
`QiWeiService` 直接使用 `content`，`DingDingService` 直接使用 `text`。
v2.x 的 `Markdown` 保留了这两套字段并由工厂方法**一并填充**，对调用方 100% 兼容。

---

---

## 5. 架构图

```
                ┌──────────────────────────────────────┐
                │              Business                │
                └────────────────────┬─────────────────┘
                                     │ 调用
                                     ▼
                ┌──────────────────────────────────────┐
                │     Monitor（统一门面入口）          │
                │     ofDingTalk / ofWeCom / ofFeishu  │
                └────────────────────┬─────────────────┘
                                     │
         ┌───────────────────────────┼───────────────────────────┐
         │                           │                           │
         ▼                           ▼                           ▼
  ┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
  │  channel/dingtalk/│       │  channel/wecom/  │       │  channel/feishu/ │
  │  DingTalkSender  │       │  WeComSender     │       │  FeishuSender    │
  │  DingTalkClient  │       │  WeComClient     │       │  FeishuClient    │
  └────────┬─────────┘       └─────────┬────────┘       └─────────┬────────┘
           │                          │                           │
           ▼                          ▼                           ▼
  oapi.dingtalk.com         qyapi.weixin.qq.com         open.feishu.cn/open-apis/bot/v2/hook

  ┌──────────────────────────────────────────────────────────┐
  │   runtime/ApplicationStartReporter （git.properties）    │
  └──────────────────────────────────────────────────────────┘
                │
                ▼
          message/* DTO（At / Markdown / Message / Text / CodeVersion）
```

---

## 6. v1 → v2 迁移指南

### 6.1 包路径迁移（一定需要改）

| v1.x 旧路径 | v2.x 新路径 |
| --- | --- |
| `io.ddd4j.extension.monitor.api.*` | _已删除_（健康检查端点由上层 Web 框架提供） |
| `io.ddd4j.extension.monitor.application.service.*` | `io.ddd4j.extension.monitor.*` 顶层包（`Sender` + `Monitor`）/ `io.ddd4j.extension.monitor.runtime.*`（`ApplicationStartReporter`） |
| `io.ddd4j.extension.monitor.domain.*` | `io.ddd4j.extension.monitor.channel.*` / `io.ddd4j.extension.monitor.message.*` |
| `io.ddd4j.extension.monitor.infras.*` | `io.ddd4j.extension.monitor.config.*` / `io.ddd4j.extension.monitor.util.*`（已删，迁移到 `IpKit`） |
| `io.ddd4j.extension.monitor.domain.dingding.service.DingDingService` | `io.ddd4j.extension.monitor.channel.dingtalk.DingTalkClient` |
| `io.ddd4j.extension.monitor.domain.qiwei.service.QiWeiService` | `io.ddd4j.extension.monitor.channel.wecom.WeComClient` |
| _v1 无_ | `io.ddd4j.extension.monitor.channel.feishu.FeishuClient`（v2.x 新增） |

### 6.2 类名迁移

| v1.x | v2.x |
| --- | --- |
| `DingDingRobotSender` | **`DingTalkRobotSender`** |
| `DingDingProperties` | **`DingTalkProperties`** |
| `QiWeiRobotSender` | **`WeComRobotSender`** |
| `QiWeiProperties` | **`WeComProperties`** |
| `QiWeiRobot`（嵌套类） | **`WeComRobot`** |
| `Sender`（v1 interface，已存在） | `core/Sender`（v2 移到 `core/`，契约不变） |
| `CodeVersionService` | `ApplicationStartReporter` |
| `HealthController` | `HealthEndpoint` |
| `AtVO / TextVO / MarkDownVO / MsgVO / CodeVersionVO` | `At / Text / Markdown / Message / CodeVersion` |
| `IpUtils.getLocalAddress()` | `io.ddd4j.kit.web.IpKit.getLocalAddress()` |

### 6.3 方法 / 字段迁移

| v1.x | v2.x |
| --- | --- |
| `properties.getLog().getDingding()` | `properties.getLog().getDingtalk()` |
| `properties.getLog().getQiwei()` | `properties.getLog().getWecom()` |
| `config.dingDingRobotSender(props)` | `config.dingTalkRobotSender(props)` |
| `config.qiWeiRobotSender(props)` | `config.wecomRobotSender(props)` |
| `Monitor.ofDingDing(...)`（v1 旧门面） | `Monitor.ofDingTalk(...)`（v2 新门面，直接返回 `DingTalkRobotSender`） |
| `Monitor.ofWeCom(...)` | `Monitor.ofWeCom(...)`（契约不变） |

### 6.4 yaml / properties 配置迁移

```yaml
# --- before (v1) ---
monitor:
  log:
    dingding:
      enable: true
      token: xxx
      secret: xxx
    qiwei:
      enable: true
      key: xxx
    rateLimiterPermitsPerSecond: 0.2857
    config:
      logLevel: ERROR
      includes: [...]
      ignores: [...]
      keywordExpression: "..."
      asyncAppenderQueueSize: 256
      # ... 一堆 Logback 专属字段

# --- after (v2) ---
monitor:
  log:
    dingtalk:                 # was dingding
      enable: true
      token: xxx
      secret: xxx
    wecom:                    # was qiwei
      enable: true
      key: xxx
    feishu:                   # v2.x 新增（钉钉/企微之后国内三大平台的最后一块拼图）
      enable: true
      webhookUrl: https://open.feishu.cn/open-apis/bot/v2/hook/xxx
      secret: xxx            # 加签密钥，"不勾选签名校验"时留空
    rateLimiterPermitsPerSecond: 0.2857
    # ✨ Logback 专属字段全部移除（asyncAppenderQueueSize / keywordExpression 等）
```

### 6.5 ⚠️ 重大行为变更：移除 Logback 集成

v1 中 `RobotLogbackAppendService` / `RobotAppender` / `RobotLayout` 等 Logback
专属类（合计 562 行）已**彻底删除**——本工具库不再持有任何与具体日志框架
强绑定的类。

**如果你之前依赖 "把日志框架的 logger 输出通过机器人告警" 这条路径**，请改为：

1. 在自家日志框架（Logback / log4j）里写一个普通的 `Appender` / `AppenderBase`，
2. 在 `append()` 中构造 `Sender`（由 `Monitor.ofDingTalk(...)` 提供），
3. 调用 `sender.send(formattedMessage)` 即可。

工具库保留 `Sender` SPI 和 `Message` DTO，**与日志框架解耦**。

### 6.6 速率限制（基于 Guava）

v1 的 `rateLimiterPermitsPerSecond = 0.2857` 默认限速依赖 Guava `RateLimiter`，
v2 已删除 Guava 依赖。**如需限速请在调用方自行实现**（如 AOP、Resilience4j 等）。

---

## 7. 验证

```bash
./mvnw -pl ddd4j-extensions/ddd4j-extension-monitor test
```

预期：`Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`（`BaseMonitorConfigTest` 11 + `SenderTest` 4 + `DingTalkClientTest` 1 + `WeComClientTest` 1 + `FeishuClientTest` 2）。

---

## 8. 后续演进（提案）

- 增加 `dingtalk-stream` 流式通道支持（用于"卡片 + 回调"场景）。
- 飞书 `msg_type=interactive`（消息卡片）的便捷构建器。
- `Message`、`Markdown` 升级为 sealed 类或 jdk21 record。
- 添加 OpenTelemetry 风格的可观测性埋点。

