# 发布性能报告契约

`ddd4j` 不将微基准框架打入发布依赖。每个发布候选由固定的 CI Runner 运行性能套件，并归档 JSON 报告；报告至少覆盖：

- `mq-outbox-dispatch`：Outbox 领取、发送、确认路径。
- `idempotency-lease`：共享缓存 CAS 获取、完成、释放路径。
- `web-request-contract`：统一 Web 上下文、鉴权和响应映射路径。

每个场景必须记录 `p95Millis`、`throughputPerSecond`、`rssMegabytes`、提交 SHA、Java 版本、运行器型号、预热和测量次数。与首个 2.0.x GA 基线比较时，任一延迟或内存指标增加超过 20%，或吞吐下降超过 20%，均阻断发布。

报告的最小格式如下。`commit` 必须是候选提交的 40 位 SHA，`rawOutputSha256` 对应固定 Runner 的原始输出，基线版本不得是 SNAPSHOT：

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

发布候选在归档前执行：

```bash
DDD4J_PERFORMANCE_REPORT=/secure-artifacts/performance.json \
  ./scripts/verify-performance-report.sh
```

校验器要求三个场景都存在，并直接计算 20% 回归阈值；报告不对应当前候选提交、缺少基线或由 Java 17 以外运行时生成时会失败关闭。

报告不能在开发机手工填写。发布候选需要保存原始输出、生成脚本、基线版本和比较结果，并由 CI 将其作为工件归档。
