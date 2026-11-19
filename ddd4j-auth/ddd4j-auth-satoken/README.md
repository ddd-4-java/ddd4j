# ddd4j-auth-satoken

`ddd4j-auth-satoken` 是纯 Java 的 Sa-Token `Subject` 适配，不提供 Spring Boot 自动装配，也不提供 OAuth 授权服务器。

## 生产启动配置

应用必须在开始接收请求前安装一次安全配置。`tokenNamespace` 同时决定 token 名称和 Sa-Token 分布式 key 前缀；JWT Simple
使用服务端 token 映射，因此支持登出、踢人和 TTL 到期后的立即失效。

```java
SaTokenSecurityProperties security = new SaTokenSecurityProperties();
security.setTokenNamespace("order-service");
security.setLoginType("user");
security.setTokenTtlSeconds(7_200L);
security.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
security.setJwtSecretKey(System.getenv("DDD4J_SATOKEN_JWT_SECRET"));
security.setJwtIssuer("order-service");
security.setJwtAudience("order-clients");
SaTokenSecurityConfigurer.configure(security);
```

JWT Simple 的密钥至少为 32 个 UTF-8 字节，`issuer` 与 `audience` 必填。`Subject.verify(token)` 会校验 JWT
签名、账号体系、issuer、audience 和服务端 token 映射；任一校验失败或认证基础设施异常均返回未认证结果，不会回退到线程主体。

权限和角色不缓存在 JWT 中，始终经 `SubjectDataProvider` 查询。因此业务侧权限变化会在下一次鉴权时生效；若数据源自身带缓存，应由业务配置相应失效策略。

无状态 JWT 不属于该模块的生产支持范围，因为它不能同时满足 `AuthPrincipal` 恢复和服务端撤销语义。

## 账号体系与会话有效期

通过 `SubjectKit.getSubject("admin")` 或 `SubjectKit.getSubject("user")` 获取对应账号体系的主体。
主体查询、权限桥、刷新和注销都使用该体系。应用应确保同一客户端提交的是对应体系签发的令牌。

未指定登录有效期时沿用该体系的 `StpLogic` 配置。显式设置 `-1` 保留永久有效的语义。
单次登录如需独立空闲窗口，应在对应逻辑配置上开启 Sa-Token 的 `dynamicActiveTimeout`。
`refresh()` 只续期当前有效会话，成功返回原令牌，采用该体系配置的长期 TTL。
缺少令牌、令牌已撤销或冻结、终端记录缺失时抛出 `NotLoggedInException`。
显式注销也会清理当前已冻结的令牌。

集成 RedisTemplate 和 Jackson 时，模块声明了 `AuthPrincipal` 和 `RolePair` 两种会话类型。
在主体扩展字段中存入其他多态业务对象时，仍需按 Sa-Token 规则声明相应类型。
RedisTemplate 更新已有值的路径要求 Redis 6.0 或更高版本。

真实 Redis 回归可用已有的本地 Redis 实例运行。将 `PORT` 换成本机测试端口。

```shell
./mvnw -pl ddd4j-auth/ddd4j-auth-satoken -am -Dtest=SaTokenSubjectRedisTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dddd4j.test.redis.port=PORT test
```
