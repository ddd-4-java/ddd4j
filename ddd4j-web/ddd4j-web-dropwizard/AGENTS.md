# ddd4j-web-dropwizard

Dropwizard Bundle 向 Jersey 注册的请求和响应组件。

由 Bundle 向 Jersey 注册 Web 组件。响应过滤器写 header 本身也可能失败；在这条路径恢复外层上下文、关闭 OTel scope，并允许同一幂等请求重试。

实现入口见 [Ddd4jDropwizardWebBundle](src/main/java/io/ddd4j/web/dropwizard/Ddd4jDropwizardWebBundle.java)。

## 回归

优先运行 [Ddd4jDropwizardWebBundleTest](src/test/java/io/ddd4j/web/dropwizard/Ddd4jDropwizardWebBundleTest.java)、[Ddd4jDropwizardOtelScopeTest](src/test/java/io/ddd4j/web/dropwizard/Ddd4jDropwizardOtelScopeTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-dropwizard -am -Dtest=Ddd4jDropwizardWebBundleTest,Ddd4jDropwizardOtelScopeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-dropwizard -am verify
```
