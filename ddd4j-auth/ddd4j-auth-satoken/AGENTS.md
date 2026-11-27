# ddd4j-auth-satoken

Sa-Token 登录主体、JWT 与多实例注册。

每个逻辑实例应使用自己的签名密钥。有效签名之外还须验证 issuer、audience 和 realm；改变实例注册顺序时核对会话配置的覆盖和恢复，避免一个 realm 的凭证在另一个 realm 生效。

实现入口见 [SaTokenSubject](src/main/java/io/ddd4j/auth/satoken/subject/SaTokenSubject.java)、[SaTokenSecurityConfigurer](src/main/java/io/ddd4j/auth/satoken/config/SaTokenSecurityConfigurer.java)。

## 回归

优先运行 [Ddd4jStpLogicJwtForSimpleTest](src/test/java/io/ddd4j/auth/satoken/config/Ddd4jStpLogicJwtForSimpleTest.java)、[SaTokenRuntimeRegistrationTest](src/test/java/io/ddd4j/auth/satoken/config/SaTokenRuntimeRegistrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-satoken -am -Dtest=Ddd4jStpLogicJwtForSimpleTest,SaTokenRuntimeRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-satoken -am verify
```
