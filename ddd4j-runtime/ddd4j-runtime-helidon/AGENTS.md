# ddd4j-runtime-helidon

Helidon MP 的 CDI 扩展、配置与显式投影。

CDI 扩展负责持有与释放运行时引用。显式投影启动失败时回滚已取得的引用；关闭多个资源时保留首个失败并继续释放余项。改发现或配置时通过服务器启动测试确认 CDI 注册生效。

实现入口见 [Ddd4jHelidonExtension](src/main/java/io/ddd4j/helidon/Ddd4jHelidonExtension.java)、[Ddd4jHelidonRuntime](src/main/java/io/ddd4j/helidon/Ddd4jHelidonRuntime.java)。

## 回归

优先运行 [Ddd4jHelidonExtensionTest](src/test/java/io/ddd4j/helidon/Ddd4jHelidonExtensionTest.java)、[HelidonServerBootTest](src/test/java/io/ddd4j/helidon/HelidonServerBootTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am -Dtest=Ddd4jHelidonExtensionTest,HelidonServerBootTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am verify
```
