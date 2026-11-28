# ddd4j-runtime-spring

Spring Bean 发现、命令事务与投影调度。

## 实现与调用

从 [SpringCommandBus](src/main/java/io/ddd4j/spring/command/SpringCommandBus.java)、[ApplicationService](src/main/java/io/ddd4j/spring/annotation/ApplicationService.java)、[DomainRepository](src/main/java/io/ddd4j/spring/annotation/DomainRepository.java) 进入。

修改 `SpringCommandBus` 时，核对公开入口 `afterSingletonsInstantiated`、`execute` 的调用方。

## 回归

- [SpringDddAnnotationFusionTest](src/test/java/io/ddd4j/spring/annotation/SpringDddAnnotationFusionTest.java) 中可从 `dddAnnotationsShouldExposeSpringMetaAnnotations`、`springShouldResolveComposedAnnotations` 定位断言。

- [SpringRuntimeContractTest](src/test/java/io/ddd4j/spring/context/SpringRuntimeContractTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am -Dtest=SpringDddAnnotationFusionTest,SpringRuntimeContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am verify
```

通过 `@Import(SpringCommandConfig.class)` 装配标准命令总线。投影入口位于 `io.ddd4j.spring.projection`。
