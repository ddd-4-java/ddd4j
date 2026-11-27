# ddd4j-runtime-spring

Spring Bean 发现、命令事务与投影调度。

命令处理器由 Spring 装配。改变路由或事务边界时经代理调用命令，让处理失败一次并观察事务结果。投影调度有独立的生命周期；改 handler 注册和改调度分别用对应入口复现。

实现入口见 [SpringCommandBus](src/main/java/io/ddd4j/spring/command/SpringCommandBus.java)、[SpringProjectionScheduler](src/main/java/io/ddd4j/data/projection/spring/SpringProjectionScheduler.java)。

这里有两个同名 SpringCommandBus。示例使用 io.ddd4j.spring.command 包下由 Spring 容器发现处理器的实现；io.ddd4j.data.cqrs.spring 包下的实现接收 CommandRegistry。改其中一个时检查导入类型与构造方式，分别验证原有调用方。

## 回归

优先运行 [SpringCommandTransactionTest](src/test/java/io/ddd4j/spring/command/SpringCommandTransactionTest.java)、[SpringCommandBusTest](src/test/java/io/ddd4j/spring/command/SpringCommandBusTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am -Dtest=SpringCommandTransactionTest,SpringCommandBusTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am verify
```
