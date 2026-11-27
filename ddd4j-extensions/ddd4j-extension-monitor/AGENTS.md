# ddd4j-extension-monitor

钉钉、飞书与企微机器人的通知发送。

消息体和签名按机器人协议分别维护。改飞书请求时覆盖配置 secret 和未配置 secret 的两种报文；改钉钉 Markdown 时验证 title、text 及提醒列表形成有效 JSON。测试使用捕获到的 HTTP 请求核对实际发送内容。

实现入口见 [DingTalkClient](src/main/java/io/ddd4j/extension/monitor/channel/dingtalk/DingTalkClient.java)、[FeishuClient](src/main/java/io/ddd4j/extension/monitor/channel/feishu/FeishuClient.java)、[WeComClient](src/main/java/io/ddd4j/extension/monitor/channel/wecom/WeComClient.java)。

## 回归

优先运行 [DingTalkClientTest](src/test/java/io/ddd4j/extension/monitor/channel/dingtalk/DingTalkClientTest.java)、[FeishuClientTest](src/test/java/io/ddd4j/extension/monitor/channel/feishu/FeishuClientTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-monitor -am -Dtest=DingTalkClientTest,FeishuClientTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-monitor -am verify
```
