# ddd4j-cache

本地与远程缓存的选择、CAS 及过期语义。

先按实际后端复现。同一进程里的 CAS 问题从本地实现的一致性用例入手；Redis 往返从 Lettuce 的测试入手。改后端选择时还要用只配置本地缓存的调用方创建实例，检查可选 Redis 客户端是否意外变成启动依赖。

实现入口见 [CacheKit](src/main/java/io/ddd4j/cache/CacheKit.java)。

## 回归

优先运行 [LocalCacheCasParityTest](src/test/java/io/ddd4j/cache/local/LocalCacheCasParityTest.java)、[LettuceCacheRedisTest](src/test/java/io/ddd4j/cache/lettuce/LettuceCacheRedisTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-cache -am -Dtest=LocalCacheCasParityTest,LettuceCacheRedisTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-cache -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
