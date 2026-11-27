# ddd4j-auth-shiro

Shiro 主体、会话、角色与权限委托。

## 实现与调用

从 [ShiroSubjectProvider](src/main/java/io/ddd4j/auth/shiro/subject/ShiroSubjectProvider.java)、[ShiroSubject](src/main/java/io/ddd4j/auth/shiro/subject/ShiroSubject.java) 进入。

修改 `ShiroSubject` 时，核对公开入口 `verify`、`isPermitted`、`isPermittedAll`、`isPermittedAny` 的调用方。

## 回归

- [ShiroSubjectProviderTest](src/test/java/io/ddd4j/auth/shiro/subject/ShiroSubjectProviderTest.java) 中可从 `getSubject_shouldReturnNonNullSubject`、`getSubject_shouldReturnShiroSubjectInstance` 定位断言。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-shiro -am -Dtest=ShiroSubjectProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-shiro -am verify
```
