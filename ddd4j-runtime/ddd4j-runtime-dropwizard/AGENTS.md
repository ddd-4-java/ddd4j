# ddd4j-runtime-dropwizard

Dropwizard Bundle 的资源注册、就绪检查与关闭。

配置显式读侧时，缺少 reader 或 repository 应在分配资源、修改全局状态前失败。健康检查对外区分就绪与不可用；检查报告不要泄露底层异常详情。

实现入口见 [Ddd4jBundle](src/main/java/io/ddd4j/dropwizard/Ddd4jBundle.java)、[Ddd4jDropwizardReadinessHealthCheck](src/main/java/io/ddd4j/dropwizard/Ddd4jDropwizardReadinessHealthCheck.java)。

## 回归

优先运行 [Ddd4jBundleCollectionContractTest](src/test/java/io/ddd4j/dropwizard/Ddd4jBundleCollectionContractTest.java)、[Ddd4jDropwizardReadinessHealthCheckTest](src/test/java/io/ddd4j/dropwizard/Ddd4jDropwizardReadinessHealthCheckTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am -Dtest=Ddd4jBundleCollectionContractTest,Ddd4jDropwizardReadinessHealthCheckTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am verify
```
