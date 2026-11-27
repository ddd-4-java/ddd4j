# ddd4j-auth-satoken

Sa-Token 登录主体、JWT 与多实例注册。

## 实现与调用

从 [SaTokenSecurityConfigurer](src/main/java/io/ddd4j/auth/satoken/config/SaTokenSecurityConfigurer.java)、[SaTokenSubject](src/main/java/io/ddd4j/auth/satoken/subject/SaTokenSubject.java)、[SaTokenSubjectProvider](src/main/java/io/ddd4j/auth/satoken/subject/SaTokenSubjectProvider.java) 进入。

修改 `SaTokenSecurityConfigurer` 时，核对公开入口 `configure` 的调用方。

修改 `SaTokenSubject` 时，核对公开入口 `login`、`logout`、`kickout`、`refresh` 的调用方。

## 回归

- [SaTokenSecurityConfigurerTest](src/test/java/io/ddd4j/auth/satoken/config/SaTokenSecurityConfigurerTest.java) 中可从 `configure_shouldInstallNamespacedSessionConfiguration`、`configure_shouldRejectJwtWithoutProductionSecret` 定位断言。

- [SaTokenSubjectDataBridgeTest](src/test/java/io/ddd4j/auth/satoken/subject/SaTokenSubjectDataBridgeTest.java) 中可从 `getPermissionListDelegatesToDataProvider`、`getPermissionListReturnsEmptyForUnknownAccount` 定位断言。

- [SaTokenSubjectLifecycleTest](src/test/java/io/ddd4j/auth/satoken/subject/SaTokenSubjectLifecycleTest.java) 中可从 `loginBuildsFullSessionFromConfig`、`loginWithoutSessionConfigUsesDefaults` 定位断言。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-satoken -am -Dtest=SaTokenSecurityConfigurerTest,SaTokenSubjectDataBridgeTest,SaTokenSubjectLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-satoken -am verify
```
