# ddd4j-auth-security

Spring Security 身份及异常到 DDD4J 的映射。

Spring Security 的匿名 token 也可出现在 SecurityContext 中，读取到 Authentication 对象仍需判断是否为匿名。错误映射分别验证认证异常的 401 和锁定账号的 403。

实现入口见 [SecuritySubject](src/main/java/io/ddd4j/auth/security/subject/SecuritySubject.java)、[SecurityExceptionHandler](src/main/java/io/ddd4j/auth/security/handler/SecurityExceptionHandler.java)。

## 回归

优先运行 [SecuritySubjectTest](src/test/java/io/ddd4j/auth/security/subject/SecuritySubjectTest.java)、[SecurityExceptionHandlerTest](src/test/java/io/ddd4j/auth/security/handler/SecurityExceptionHandlerTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-security -am -Dtest=SecuritySubjectTest,SecurityExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-security -am verify
```
