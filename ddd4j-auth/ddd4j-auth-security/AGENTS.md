# ddd4j-auth-security

Spring Security 身份及异常到 DDD4J 的映射。

## 实现与调用

从 [SecurityExceptionHandler](src/main/java/io/ddd4j/auth/security/handler/SecurityExceptionHandler.java)、[SecuritySubject](src/main/java/io/ddd4j/auth/security/subject/SecuritySubject.java)、[SecuritySubjectProvider](src/main/java/io/ddd4j/auth/security/subject/SecuritySubjectProvider.java) 进入。

修改 `SecurityExceptionHandler` 时，核对公开入口 `authenticationException`、`lockedException`、`accessDeniedException` 的调用方。

修改 `SecuritySubject` 时，核对公开入口 `login`、`logout`、`kickout`、`refresh` 的调用方。

## 回归

- [SecuritySubjectProviderTest](src/test/java/io/ddd4j/auth/security/subject/SecuritySubjectProviderTest.java) 中可从 `getSubject_shouldReturnNonNullSubject`、`getSubject_shouldReturnSecuritySubjectInstance` 定位断言。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-security -am -Dtest=SecuritySubjectProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-security -am verify
```
