# 商品交易样例测试工具

本模块提供实际 HTTP 传输和响应契约断言。测试调用各框架真实启动的 HTTP 服务，在专用 MySQL 库中验证商品交易和鉴权行为。共享的交易流程与数据库设置见[示例总览](../README.md)。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-order-testkit -am test
```
