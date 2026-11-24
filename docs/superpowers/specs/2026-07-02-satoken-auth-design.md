# Sa-Token 认证授权接入设计

- **日期**：2026-07-02
- **作者**：ddd4j 架构团队
- **状态**：已实施

## 1. 目标与范围

定义 ddd4j-auth-satoken 的能力概览、关键类路径、快速接入方式和最佳实践。

## 2. 能力概览

- **集成组件**：`ddd4j-auth/ddd4j-auth-satoken`
- **Spring 桥接**：`ddd4j-auth/ddd4j-auth-spring` 通过 `SubjectRegistrar` 将 `SubjectProvider` 注册到 `SubjectKit`
- **扩展工具**：`StpKit` 提供扩展载荷读取与类型转换、`SaTempKit` 提供临时授权码能力

## 3. 关键类与路径

| 类                    | 路径                                                                                                    |
|-----------------------|---------------------------------------------------------------------------------------------------------|
| Sa-Token Subject 实现 | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/subject/SaTokenSubject.java`         |
| SubjectProvider 实现  | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/subject/SaTokenSubjectProvider.java` |
| 扩展常量              | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/SaConstants.java`                    |
| 载荷读取              | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/util/StpKit.java`                    |
| 临时 Token            | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/util/SaTempKit.java`                 |

## 4. 快速接入

1. 引入依赖（外部项目建议通过 BOM）：`ddd4j-bom` 中声明了 `ddd4j-auth-satoken` 版本
2. 配置 Sa-Token 基本属性（如 Token 风格、过期时间、是否 JWT 等）
3. 在登录成功后，写入必要的扩展载荷（如用户、机构、角色等）

## 5. 通用载荷字段与读取示例

| 字段 | Sa-Token 常量 | 键值 |
|---|---|---|
| 租户 ID | `SaConstants.PAYLOAD_TENANT_ID` | `tenant_id` |
| 应用 ID | `SaConstants.PAYLOAD_APP_ID` | `appId` |
| 角色 ID | `SaConstants.PAYLOAD_ROLE_ID` | `rid` |
| 角色编码 | `SaConstants.PAYLOAD_ROLE_CODE` | `roleCode` |
| 角色类型 | `SaConstants.PAYLOAD_ROLE_TYPE` | `roleType` |
| 用户 ID | `SaConstants.PAYLOAD_USER_ID` | `uid` |
| 机构 ID | `SaConstants.PAYLOAD_ORG_ID` | `org_id` |

键值由 `AuthConstants` 统一维护。签发时使用 `SaConstants.PAYLOAD_TENANT_ID` 写入标准租户字段 `tenant_id`；JWT Simple 会原样保留调用方传入的扩展键，通用读取器也按指定键精确取值。用户、机构、角色 ID 的专用读取方法优先读取表中约定的字段，缺值时兼容 `userId`、`orgId`、`roleId`。应用专用载荷键由应用维护，通过 `StpKit.getExtraAs(...)` 读取。接入使用 `user_id` 等其他键名的存量令牌时，在接入层完成字段映射。

```java
String userId = StpKit.getUserIdAsString();
Long orgId = StpKit.getOrgIdAsLong();
String roleId = StpKit.getRoleIdAsString();
String tenantId = StpKit.getExtraAsString(SaConstants.PAYLOAD_TENANT_ID);
String roleType = StpKit.getExtraAsString(SaConstants.PAYLOAD_ROLE_TYPE);
```

## 6. 临时授权码（一次性 Token）

- 校验逻辑：`SaTempKit.java`
- 典型使用：签发临时码用于敏感操作确认，后端校验通过后立即失效

## 7. 最佳实践

- 所有鉴权受保护的接口统一走 Sa-Token 拦截
- 在业务层优先通过 `SubjectKit` 读取认证主体和权限语义，必要时再使用 `StpUtil`/`StpKit`
- 对 JWT 载荷的扩展键使用 `SaConstants` 约定字段，避免语义不一致
- 对管理端/移动端区分 `deviceType`，用于风控与日志聚合

## 8. P0 会话契约（2.0.x）

- Sa-Token 依赖由中心 BOM 统一至 1.46.0。三个下游脚手架的装配维持现有接口。
- JWT Simple 的签发与验签使用已校验的密钥、issuer 和 audience。服务端 Token 映射决定到期与撤销。
- 按 realm 获取的 Subject 对同一账号体系执行会话读写、鉴权、刷新和注销。原生权限桥依据 loginType 加载主体。
- 登录基于当前账号体系的参数构建器。未指定的长期有效期沿用逻辑配置，显式 -1 仍表示永久。映射设备标识、挤占策略、预定令牌和立即创建 Token-Session。
- refresh 续期当前有效凭证，成功返回同一令牌。无令牌、撤销、冻结或缺少完整会话时抛出未登录异常。续期保留最后活跃时间。
- 验收包括同 ID 的 admin/user、真实 Redis 中的键与 TTL、刷新及注销后凭证失效。
