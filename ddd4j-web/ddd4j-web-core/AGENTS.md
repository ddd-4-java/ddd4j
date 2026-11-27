# ddd4j-web-core

HTTP 请求上下文、身份策略、幂等和异常翻译。

新增认证头格式前先核对 Authorization scheme 的解析。身份策略和已知异常的状态翻译在公共层维护，调用框架负责写 HTTP 响应。改请求对象时验证已捕获的 method、path 和上下文在后续处理中仍可读。

实现入口见 [BearerTokenResolver](src/main/java/io/ddd4j/web/core/auth/BearerTokenResolver.java)、[BearerSubjectAuthenticator](src/main/java/io/ddd4j/web/core/auth/BearerSubjectAuthenticator.java)。

## 回归

优先运行 [WebCoreContractTest](src/test/java/io/ddd4j/web/core/WebCoreContractTest.java)、[BearerSubjectAuthenticatorTest](src/test/java/io/ddd4j/web/core/auth/BearerSubjectAuthenticatorTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-core -am -Dtest=WebCoreContractTest,BearerSubjectAuthenticatorTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-core -am verify
```
