# 发布性能报告契约设计

- **日期**：2026-08-03
- **作者**：ddd4j 架构团队
- **状态**：已定义

## 1. 目标与范围

定义 ddd4j 发布候选的性能报告契约。ddd4j 不将微基准框架打入发布依赖，每个发布候选由固定的 CI Runner 运行性能套件并归档 JSON 报告。

## 2. 必须覆盖的场景

| 场景 | 说明 |
|------|------|
| `mq-outbox-dispatch` | Outbox 领取、发送、确认路径 |
| `idempotency-lease` | 共享缓存 CAS 获取、完成、释放路径 |
| `web-request-contract` | 统一 Web 上下文、鉴权和响应映射路径 |

## 3. 每个场景必须记录的指标

- `p95Millis`：P95 延迟
- `throughputPerSecond`：吞吐量
- `rssMegabytes`：RSS 内存
- `commit`：提交 SHA
- `javaVersion`：Java 版本
- `runnerId`：运行器型号
- `warmupIterations`：预热次数
- `measurementIterations`：测量次数

## 4. 回归阈值

与首个 2.0.x GA 基线比较时：
- 任一延迟或内存指标增加超过 **20%** → 阻断发布
- 吞吐下降超过 **20%** → 阻断发布

## 5. 报告最小格式

```json
{
  "schemaVersion": "1.0",
  "reportKind": "ddd4j-performance",
  "commit": "40 位候选提交 SHA",
  "baseline": {
    "version": "2.0.0",
    "commit": "40 位 GA 基线 SHA"
  },
  "environment": {
    "runnerId": "固定 Runner 标识",
    "javaVersion": "17.0.x",
    "warmupIterations": 10,
    "measurementIterations": 20
  },
  "generator": {
    "command": "性能套件的可复现执行命令"
  },
  "rawOutputSha256": "原始输出的 SHA-256",
  "scenarios": [
    {
      "name": "mq-outbox-dispatch",
      "p95Millis": 1.2,
      "throughputPerSecond": 1000,
      "rssMegabytes": 256,
      "baseline": {
        "p95Millis": 1.0,
        "throughputPerSecond": 1100,
        "rssMegabytes": 240
      }
    }
  ]
}
```

## 6. 验证命令

```bash
DDD4J_PERFORMANCE_REPORT=/secure-artifacts/performance.json \
  ./scripts/verify-performance-report.sh
```

校验器要求三个场景都存在，并直接计算 20% 回归阈值；报告不对应当前候选提交、缺少基线或由 Java 17 以外运行时生成时会失败关闭。

## 7. 约束

- 报告不能在开发机手工填写
- 发布候选需要保存原始输出、生成脚本、基线版本和比较结果
- CI 将其作为工件归档
