# ddd4j-web-testkit

跨服务器的 HTTP 测试契约与客户端夹具。

## 实现与调用

从 [AbstractWebContractTest](src/main/java/io/ddd4j/web/testkit/AbstractWebContractTest.java)、[WebContractClient](src/main/java/io/ddd4j/web/testkit/WebContractClient.java)、[WebContractPaths](src/main/java/io/ddd4j/web/testkit/WebContractPaths.java) 进入。

## 回归

本工件没有独立测试类。修改公开接口后，另选实际消费模块执行行为回归。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-testkit -am verify
```
