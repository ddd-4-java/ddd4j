# ddd4j-runtime-guice

Guice 绑定、可选能力与 Javalin 运行时接线。Guice 应用通过 `Ddd4jCommandGuiceModule` 注入 `CommandBus`。Javalin 应用直接调用 `io.ddd4j.guice.command.javalin.JavalinCommandBus.create`，投影调度和视图管理在 `io.ddd4j.guice.projection.javalin`。

Guice 的基础装配可以运行在裁掉可选组件的 JVM。改 Module.bind 或 provider 签名时，运行独立 JVM 的 optional capabilities 用例；全量依赖下创建 Injector 无法暴露可选类在加载阶段被强制解析的问题。

实现入口见 [Ddd4jGuiceModule](src/main/java/io/ddd4j/guice/Ddd4jGuiceModule.java)。

## 回归

优先运行 [Ddd4jGuiceModuleTest](src/test/java/io/ddd4j/guice/Ddd4jGuiceModuleTest.java)、[GuiceOptionalCapabilitiesTest](src/test/java/io/ddd4j/guice/GuiceOptionalCapabilitiesTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am -Dtest=Ddd4jGuiceModuleTest,GuiceOptionalCapabilitiesTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am verify
```
