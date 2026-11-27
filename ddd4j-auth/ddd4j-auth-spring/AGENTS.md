# ddd4j-auth-spring

Spring 容器的主体注册与鉴权异常分派。

主体注册和 MVC 异常分派分别维护。改鉴权返回时验证认证失败与授权失败的差异；已有异常消息应保留，缺失消息时才采用默认原因。用 Spring 的真实分派路径测试处理器选择。

实现入口见 [SubjectRegistrar](src/main/java/io/ddd4j/auth/spring/SubjectRegistrar.java)、[AuthSpringConfiguration](src/main/java/io/ddd4j/auth/spring/AuthSpringConfiguration.java)。

## 回归

优先运行 [SubjectRegistrarTest](src/test/java/io/ddd4j/auth/spring/SubjectRegistrarTest.java)、[SaTokenExceptionDispatchContractTest](src/test/java/io/ddd4j/auth/spring/SaTokenExceptionDispatchContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-spring -am -Dtest=SubjectRegistrarTest,SaTokenExceptionDispatchContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-spring -am verify
```
