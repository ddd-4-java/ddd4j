# ddd4j-web-vertx

Vert.x Router 请求上下文、提前响应与租约释放。

重点看请求还在获取幂等租约时连接被重置的分支。提前写响应或 TCP reset 都可能越过普通 handler 的结尾；释放未使用的租约后，相同 key 的下一请求应能重试。

实现入口见 [Ddd4jVertxWeb](src/main/java/io/ddd4j/web/vertx/Ddd4jVertxWeb.java)、[Ddd4jVertxContext](src/main/java/io/ddd4j/web/vertx/Ddd4jVertxContext.java)。

## 回归

优先运行 [Ddd4jVertxEarlyResponseTest](src/test/java/io/ddd4j/web/vertx/Ddd4jVertxEarlyResponseTest.java)、[Ddd4jVertxWebContractTest](src/test/java/io/ddd4j/web/vertx/Ddd4jVertxWebContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-vertx -am -Dtest=Ddd4jVertxEarlyResponseTest,Ddd4jVertxWebContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-vertx -am verify
```
