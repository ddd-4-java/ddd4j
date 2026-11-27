# ddd4j-runtime-quarkus

Quarkus CDI 命令路由与上下文生命周期。

运行时提供默认 CDI 命令总线，业务可提供替代 Bean。新增执行器要验证重复注册会在启动时失败，延迟发现的执行器仍可路由。默认 Bean 唯一性与 request context 恢复需要容器参与验证。

实现入口见 [Ddd4jQuarkusRuntime](src/main/java/io/ddd4j/quarkus/Ddd4jQuarkusRuntime.java)。

## 回归

优先运行 [QuarkusCommandBusOverrideTest](src/test/java/io/ddd4j/quarkus/command/QuarkusCommandBusOverrideTest.java)、[QuarkusCommandBusTest](src/test/java/io/ddd4j/quarkus/command/QuarkusCommandBusTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am -Dtest=QuarkusCommandBusOverrideTest,QuarkusCommandBusTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am verify
```
