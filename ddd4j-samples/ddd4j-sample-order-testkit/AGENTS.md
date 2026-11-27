# ddd4j-sample-order-testkit

可复用的 HTTP 传输、鉴权驱动及断言。

运输层遇到线程中断时关闭活动连接、恢复中断标志并传出 I/O 错误。鉴权测试按引擎选择 driver，未知或缺失引擎在调用 driver 前拒绝。新增框架驱动时复用同一套请求边界断言。

实现入口见 [SampleHttpTransport](src/main/java/io/ddd4j/sample/order/testkit/SampleHttpTransport.java)、[SampleContractAssertions](src/main/java/io/ddd4j/sample/order/testkit/SampleContractAssertions.java)。

## 回归

优先运行 [SampleHttpTransportInterruptionTest](src/test/java/io/ddd4j/sample/order/testkit/SampleHttpTransportInterruptionTest.java)、[SampleContractAssertionsTest](src/test/java/io/ddd4j/sample/order/testkit/SampleContractAssertionsTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-order-testkit -am -Dtest=SampleHttpTransportInterruptionTest,SampleContractAssertionsTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-samples/ddd4j-sample-order-testkit -am verify
```
