# ddd4j-extension-otel

业务操作的 span 与上下文传递。

装饰业务操作时必须返回被包装 supplier 或权限 provider 的结果。缓存未命中仍执行 supplier，权限拒绝必须原样传出。用 no-op OTel 验证业务，再用启用追踪的消费方核对 span。

实现入口见 [AuthSpan](src/main/java/io/ddd4j/extension/otel/AuthSpan.java)、[CacheSpan](src/main/java/io/ddd4j/extension/otel/CacheSpan.java)、[DataScopeSpan](src/main/java/io/ddd4j/extension/otel/DataScopeSpan.java)。

## 回归

优先运行 [CacheSpanTest](src/test/java/io/ddd4j/extension/otel/CacheSpanTest.java)、[DataScopeSpanTest](src/test/java/io/ddd4j/extension/otel/DataScopeSpanTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-otel -am -Dtest=CacheSpanTest,DataScopeSpanTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-otel -am verify
```
