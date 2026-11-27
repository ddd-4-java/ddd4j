# ddd4j-auth-shiro

Shiro 主体、会话、角色与权限委托。

ShiroSubject 将权限判断委托给当前 Shiro 主体。含 loginId 的 isPermitted 重载仍然查询当前主体，不能作为跨账号权限查询入口。改登录或会话时跑无 SecurityManager、匿名、设置超时以及注销后的用例。

实现入口见 [ShiroSubjectProvider](src/main/java/io/ddd4j/auth/shiro/subject/ShiroSubjectProvider.java)、[ShiroSubject](src/main/java/io/ddd4j/auth/shiro/subject/ShiroSubject.java)。

## 回归

优先运行 [ShiroSubjectTest](src/test/java/io/ddd4j/auth/shiro/subject/ShiroSubjectTest.java)、[ShiroSubjectProviderTest](src/test/java/io/ddd4j/auth/shiro/subject/ShiroSubjectProviderTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-shiro -am -Dtest=ShiroSubjectTest,ShiroSubjectProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-shiro -am verify
```
