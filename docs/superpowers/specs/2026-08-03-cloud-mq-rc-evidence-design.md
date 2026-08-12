# 云 MQ 发布候选证据设计

- **日期**：2026-08-03
- **作者**：ddd4j 架构团队
- **状态**：已定义

## 1. 目标与范围

定义 ONS 与 TDMQ 的云端认证证据要求。本地协议模拟只证明适配器兼容性，不能替代厂商云端认证。每个 2.0.x 发布候选必须在受保护 CI Environment 的真实云租户中执行一次。

## 2. 必须覆盖的 Broker

- **ONS**（阿里云消息队列）
- **TDMQ**（腾讯云消息队列）

## 3. 每条记录必须覆盖的 Checks

- `publish`：发布
- `consume`：消费
- `ack`：确认
- `retry`：重试
- `message-id`：稳定 `ddd4j-message-id`

## 4. 报告格式

```json
{
  "schemaVersion": "1.0",
  "reportKind": "ddd4j-cloud-mq-rc",
  "commit": "40 位候选提交 SHA",
  "generatedAt": "2026-08-03T12:00:00Z",
  "executions": [
    {
      "broker": "ons",
      "result": "passed",
      "region": "cn-hangzhou",
      "endpointHost": "ons.example.com",
      "clientVersion": "客户端版本",
      "checks": ["publish", "consume", "ack", "retry", "message-id"],
      "rawLogSha256": "原始日志的 SHA-256",
      "evidenceUri": "受限构件或审计记录 URI"
    }
  ]
}
```

## 5. 安全约束

- `endpointHost` 只能保存主机名
- 报告、日志和 URI 禁止保存 access key、secret、password 或 token

## 6. 验证命令

```bash
DDD4J_CLOUD_MQ_RC_REPORT=/secure-artifacts/cloud-mq-rc.json \
  ./scripts/verify-cloud-mq-rc-evidence.sh
```

报告的 `commit` 必须等于候选提交；任一 broker 缺失、失败或证据字段不完整都会失败关闭。
