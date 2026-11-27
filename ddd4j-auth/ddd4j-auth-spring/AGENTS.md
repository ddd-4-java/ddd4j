# ddd4j-auth-spring

Spring 容器的主体注册与鉴权异常分派。

## 实现与调用

从 [AuthSpringConfiguration](src/main/java/io/ddd4j/auth/spring/AuthSpringConfiguration.java)、[SubjectRegistrar](src/main/java/io/ddd4j/auth/spring/SubjectRegistrar.java)、[SaTokenExceptionHandler](src/main/java/io/ddd4j/auth/spring/satoken/SaTokenExceptionHandler.java) 进入。

修改 `AuthSpringConfiguration` 时，核对公开入口 `subjectRegistrar` 的调用方。

修改 `SubjectRegistrar` 时，核对公开入口 `postProcessAfterInitialization` 的调用方。

## 回归

- [SubjectRegistrarTest](src/test/java/io/ddd4j/auth/spring/SubjectRegistrarTest.java) 中可从 `shouldRegisterSubjectProviderAfterInitialization` 定位断言。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-spring -am -Dtest=SubjectRegistrarTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-spring -am verify
```
