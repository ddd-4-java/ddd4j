# ddd4j-auth

身份接口定义在 core；当前主体、权限和会话从各引擎适配器读取。Spring 注册与异常分派有单独的桥接模块。软件许可证走证书安装和验证路径，和 HTTP 登录会话分开维护。

## 按变更选择模块

| 工件 | 修改范围 |
|---|---|
| [ddd4j-auth-satoken](ddd4j-auth-satoken/AGENTS.md) | Sa-Token 登录主体、JWT 与多实例注册 |
| [ddd4j-auth-security](ddd4j-auth-security/AGENTS.md) | Spring Security 身份及异常到 DDD4J 的映射 |
| [ddd4j-auth-shiro](ddd4j-auth-shiro/AGENTS.md) | Shiro 主体、会话、角色与权限委托 |
| [ddd4j-auth-spring](ddd4j-auth-spring/AGENTS.md) | Spring 容器的主体注册与鉴权异常分派 |

## 验证范围

从仓库根目录执行整组验证时使用下列命令。只改一个实现时，进入该模块指引选择对应的行为测试。

```sh
./mvnw -pl ddd4j-auth -am verify
```
