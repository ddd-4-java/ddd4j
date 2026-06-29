# ddd4j-auth 模块架构优化方案

> **审查范围**：`ddd4j-auth/` 下全部 5 个子模块（datascope / license / satoken / security / shiro）
> **审查方式**：codegraph 索引 + 源码逐文件核对
> **审查时间**：2026-06-29
> **审查结论**：现状**未达标**，与 `ddd4j-mq` / `ddd4j-kit` 已达标模块存在系统性差距，需要一次性重构到可使用标准。

---

## 一、现状审查结果

### 1.1 模块清单与归属问题

| 子模块                    | 当前内容                                                 | 问题                                                  |
|------------------------|------------------------------------------------------|-----------------------------------------------------|
| `ddd4j-auth-datascope` | `DataScopeProvider` + `@RequiresDataPermissions`     | ⚠️ 与鉴权无关，是数据权限，应归 data 层                            |
| `ddd4j-auth-license`   | TrueLicense 证书管理                                     | ⚠️ 与鉴权无关，是 License 授权，应独立                           |
| `ddd4j-auth-satoken`   | `SaTokenSubject` + `SaTokenEnhanceAutoConfiguration` | ❌ `SaTokenSubject.getPrincipal()` 全返回 null（**未实现**） |
| `ddd4j-auth-security`  | `SecuritySubject` + JWT 配置                           | ❌ `SecuritySubject` 所有方法返回 false/null（**空壳**）       |
| `ddd4j-auth-shiro`     | `ShiroSubject` + `WebShiroBizConfiguration`          | ✅ 唯一完整实现，但耦合 Spring                                 |

### 1.2 核心契约位置问题（最严重）

**Subject 契约放错了模块**：

```
当前现状：
  ddd4j-core/subject/Subject.java          ← 纯 Java 契约（正确位置 ✅）
  ddd4j-core/subject/SubjectProvider.java  ← 纯 Java SPI（正确位置 ✅）
  ddd4j-core/subject/AuthPrincipal.java    ← 纯 Java 值对象（正确位置 ✅）
  ddd4j-core/util/SubjectKit.java          ← 静态注册表（正确位置 ✅）

问题：
  ddd4j-auth/pom.xml 声明 "-core(纯 Java SPI)" 模块
  但实际【没有 ddd4j-auth-core 子模块】！
  契约被放到了 ddd4j-core，导致 ddd4j-auth 聚合层是个空壳。
```

### 1.3 `SubjectKit` 注册断链（致命缺陷）

```java
// ddd4j-core/util/SubjectKit.java
public static volatile SubjectProvider subjectProvider;  // 静态字段

public static Subject getSubject() {
    if (subjectProvider == null) {
        throw new IllegalStateException(
            "SubjectProvider not registered. Call SubjectKit.register() or use framework adapter.");
    }
    return subjectProvider.getSubject();
}
```

**问题**：三个适配模块都 `@Bean public SubjectProvider subjectProvider()`，但**没有任何代码把 Bean
写回 `SubjectKit.subjectProvider` 静态字段**。也就是说：

- Spring 容器里有一个 `SubjectProvider` Bean ✅
- 但 `SubjectKit.getSubject()` 永远抛 `IllegalStateException` ❌
- 任何调用 `SubjectKit.getPrincipal()` 的业务代码都会 NPE/异常

这是**整个 auth 模块当前不可用的根本原因**。

### 1.4 三实现完整度天差地别

| 实现                | getPrincipal    | getPrincipalByLoginId | getPrincipalByToken | 完整度         |
|-------------------|-----------------|-----------------------|---------------------|-------------|
| `ShiroSubject`    | ✅ 委托 Shiro      | ⚠️ 退化为 getPrincipal   | ⚠️ 退化为 getPrincipal | 70%         |
| `SaTokenSubject`  | ❌ `return null` | ❌ `return null`       | ❌ `return null`     | 30%（仅权限/角色） |
| `SecuritySubject` | ❌ `return null` | ❌ 全 `return false`    | ❌ 全 `return false`  | **0%（空壳）**  |

### 1.5 自动配置机制不统一

| 模块                     | 自动配置注册方式                                         | 状态                 |
|------------------------|--------------------------------------------------|--------------------|
| `ddd4j-auth-datascope` | `spring/...AutoConfiguration.imports`（新机制）       | ✅                  |
| `ddd4j-auth-satoken`   | `spring-autoconfigure-metadata.properties`（仅元数据） | ❌ **未注册到 imports** |
| `ddd4j-auth-security`  | `spring-autoconfigure-metadata.properties`（仅元数据） | ❌ **未注册到 imports** |
| `ddd4j-auth-shiro`     | 无任何注册                                            | ❌ **完全缺失**         |
| `ddd4j-auth-license`   | `spring-autoconfigure-metadata.properties`（仅元数据） | ❌ **未注册到 imports** |

**结果**：除了 datascope，其余 4 个模块的 `@Configuration` 类**根本不会被 Spring Boot 自动装配**。

### 1.6 功能错位（datascope / license 不属于 auth）

- `ddd4j-auth-datascope`：数据权限（DataScope），是**查询层**关注的能力，应归 `ddd4j-data` 或独立的 `ddd4j-security` 聚合
- `ddd4j-auth-license`：TrueLicense 证书，是**软件授权**，与用户鉴权无关，应独立为 `ddd4j-license`

### 1.7 与达标模块的范式差距

对照 `ddd4j-mq`（已达标）的拆分范式：

```
ddd4j-mq/（达标范式）
├── ddd4j-mq-core/          ← 纯 Java SPI（零 Spring）
│   ├── MQEventPublisher    ← 接口
│   ├── MQBrokerAdapter     ← SPI
│   └── MQBrokerAdapters    ← 静态解析器
├── ddd4j-mq-spring/        ← Spring 桥接（注册 SPI）
├── ddd4j-mq-kafka/         ← Kafka 实现
├── ddd4j-mq-rabbitmq/      ← RabbitMQ 实现
└── ...

ddd4j-auth/（当前现状）
├── ❌ 没有 auth-core 纯 SPI 模块
├── ddd4j-auth-satoken/     ← 实现 + Spring 配置混在一起
├── ddd4j-auth-security/    ← 实现 + Spring 配置混在一起（且空壳）
├── ddd4j-auth-shiro/       ← 实现 + Spring 配置混在一起
├── ddd4j-auth-datascope/   ← 错位（不属于 auth）
└── ddd4j-auth-license/     ← 错位（不属于 auth）
```

---

## 二、目标架构（对齐 ddd4j-mq 范式）

### 2.1 模块重组

```
ddd4j-auth/（重构后）
├── ddd4j-auth-core/                ← 【新增】纯 Java SPI（零 Spring 依赖）
│   ├── subject/
│   │   ├── Subject.java            ← 核心：读取+校验+会话操作（login/logout/refresh/kickout）
│   │   ├── SubjectProvider.java    ← 核心：Subject 工厂 SPI
│   │   └── AuthPrincipal.java      ← 认证主体值对象
│   ├── SubjectKit.java             ← 核心：静态门面（业务统一入口）
│   ├── AuthRequest.java            ← 登录请求载体（loginId/principal/extra/timeout）
│   └── AuthErrorCode.java          ← 统一错误码
│
├── ddd4j-auth-spring/              ← 【新增】Spring 桥接（注册 SubjectKit）
│   ├── AuthAutoConfiguration       ← @ConditionalOnClass 自动装配
│   └── SubjectRegistrar            ← 把 SubjectProvider Bean 写回 SubjectKit
│
├── ddd4j-auth-satoken/             ← sa-token 实现（重写 Subject 完整契约）
│   └── subject/SaTokenSubject      ← 完整实现 getPrincipal + login/logout/refresh
│
├── ddd4j-auth-security/            ← Spring Security 实现（重写空壳）
│   └── subject/SecuritySubject     ← 委托 SecurityContextHolder + 登录/登出
│
└── ddd4j-auth-shiro/               ← Shiro 实现（补全 Subject 契约）
    └── subject/ShiroSubject        ← 已完整，补全 login/logout

【移出 auth 聚合】
├── ddd4j-auth-datascope → 迁移到 ddd4j-data/ddd4j-data-datascope
└── ddd4j-auth-license   → 迁移到 ddd4j-extensions/ddd4j-extension-license
```

### 2.2 核心契约设计（以 Subject 为唯一中心）

> **设计原则**：不引入 `Authentication`/`TokenResolver` 等并行抽象。所有鉴权能力（读取、校验、会话操作）统一收敛到 `Subject`
> 接口；`SubjectKit` 作为静态门面提供业务调用入口；`SubjectProvider` 作为 SPI 由三框架适配层实现。

#### 2.2.1 `Subject` 接口扩展（在现有契约基础上补全会话操作）

现有契约（保留不动）：

- 读取：`getPrincipal` / `getPrincipalByLoginId` / `getPrincipalByToken`
- 校验：`isPermitted` / `hasRole` / `isAuthenticated` / `isRemembered`
- 身份：`getLoginId` / `getUserId` / `getOrgId` / `getRoleId` / `getExtra`
- 设备：`isTrustDeviceId`

**新增会话操作能力**（统一收敛，避免并列抽象）：

```java
public interface Subject {

    // ============ 现有契约（保留）============
    <T extends AuthPrincipal> T getPrincipal();
    <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId);
    <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue);
    boolean isPermitted(String permission);
    // ... 其余校验方法保持不变 ...
    boolean isAuthenticated();

    // ============ 新增：会话生命周期 ============
    // 原 Authentication.login/logout/refresh/verify 能力直接并入 Subject

    /**
     * 登录（建立会话）。
     * @param request 登录请求（loginId + AuthPrincipal + 扩展信息 + 有效期）
     * @return 会话凭证（Token / SessionId / null 表示无状态）
     */
    String login(AuthRequest request);

    /**
     * 登出（销毁当前会话）。
     */
    void logout();

    /**
     * 按 loginId 强制登出（踢人下线）。
     */
    void logout(Object loginId);

    /**
     * 刷新会话凭证（续期 / 换发 Token）。
     * @return 新凭证
     */
    String refresh();

    /**
     * 校验凭证有效性（仅校验，不建立会话）。
     * @return 凭证对应的认证主体，校验失败返回 null
     */
    <T extends AuthPrincipal> T verify(String token);

    // ============ 现有 default 方法（保留）============
    default Object getLoginId() { ... }
    default Object getUserId() { ... }
    // ...
}
```

#### 2.2.2 `AuthRequest` 登录请求载体（替代 AuthenticationRequest）

```java
/**
 * 登录请求载体（纯 Java 值对象）。
 * 承载登录所需的全部信息，由业务构造，由 Subject 实现消费。
 */
public class AuthRequest {

    /** 账号ID（必填） */
    private Object loginId;
    /** 认证主体（可选，登录后通过 getPrincipal 取回） */
    private AuthPrincipal principal;
    /** 会话有效期（秒），-1 表示永久 */
    private long timeout = -1;
    /** 设备类型（多端登录隔离用） */
    private String deviceType;
    /** 多账号体系标识（如 sa-token 的 "admin"/"user"） */
    private String realm;
    /** 扩展信息（写入 Token Claim 或 Session） */
    private Map<String, Object> extra = new HashMap<>();

    // Builder 模式
    public static AuthRequest of(Object loginId) { ... }
    public AuthRequest principal(AuthPrincipal p) { ... }
    public AuthRequest timeout(long seconds) { ... }
    public AuthRequest device(String deviceType) { ... }
    public AuthRequest realm(String realm) { ... }
    public AuthRequest extra(String key, Object value) { ... }
}
```

#### 2.2.3 `SubjectKit` 静态门面（业务唯一入口）

```java
public final class SubjectKit {

    static volatile SubjectProvider subjectProvider;

    /** 注册 SPI（由框架适配层调用） */
    public static void register(SubjectProvider provider) {
        subjectProvider = provider;
    }

    /** 获取当前 Subject（业务统一入口） */
    public static Subject getSubject() {
        if (subjectProvider == null) {
            throw new IllegalStateException("SubjectProvider not registered.");
        }
        return subjectProvider.getSubject();
    }

    // ============ 便捷方法：读取/校验 ============
    public static <T extends AuthPrincipal> T getPrincipal() {
        return getSubject().getPrincipal();
    }
    public static boolean isLogin() {
        return getSubject().isAuthenticated();
    }
    public static boolean hasPermission(String permission) {
        return getSubject().isPermitted(permission);
    }

    // ============ 便捷方法：会话操作（原 Authentication 能力） ============
    public static String login(AuthRequest request) {
        return getSubject().login(request);
    }
    public static void logout() {
        getSubject().logout();
    }
    public static void logout(Object loginId) {
        getSubject().logout(loginId);
    }
    public static String refresh() {
        return getSubject().refresh();
    }
    public static <T extends AuthPrincipal> T verify(String token) {
        return getSubject().verify(token);
    }
}
```

#### 2.2.4 `SubjectProvider` 工厂 SPI（三框架各自实现）

```java
public interface SubjectProvider {

    /**
     * 获取当前 Subject 实例。
     * 各鉴权实现（sa-token/shiro/security）提供。
     */
    Subject getSubject();

}
```

**注册时机**（修复当前致命断链）：

```java
// Spring 桥接：SubjectRegistrar
public class SubjectRegistrar implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SubjectProvider provider) {
            SubjectKit.register(provider);  // 【关键修复】写回静态字段
        }
        return bean;
    }
}

// Quarkus/Javalin：在启动钩子中显式注册
SubjectKit.register(new SaTokenSubjectProvider());
```

### 2.3 三鉴权实现如何落地 Subject 契约

| Subject 方法           | sa-token 实现                              | Spring Security 实现                                                      | Shiro 实现                                    |
|----------------------|------------------------------------------|-------------------------------------------------------------------------|---------------------------------------------|
| `login(AuthRequest)` | `StpUtil.login(loginId, extra, timeout)` | `SecurityContextHolder.setContext(...)`                                 | `Subject.login(token)`                      |
| `logout()`           | `StpUtil.logout()`                       | `SecurityContextHolder.clearContext()`                                  | `Subject.logout()`                          |
| `logout(loginId)`    | `StpUtil.kickout(loginId)`               | 注销其 Session                                                             | `SessionDAO.delete(session)`                |
| `refresh()`          | `StpUtil.getTokenValue()`（续期）            | 重签 JWT                                                                  | 刷新 Session                                  |
| `verify(token)`      | `StpUtil.getLoginIdByToken(token)`       | `JwtParser.parseClaimsJws(token)`                                       | `SecurityUtils.getSubjectByToken`           |
| `getPrincipal()`     | `StpUtil.getSession().get("principal")`  | `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` | `SecurityUtils.getSubject().getPrincipal()` |
| `isPermitted(p)`     | `StpUtil.hasPermission(p)`               | `Authentication.getAuthorities()` 比对                                    | `SecurityUtils.getSubject().isPermitted(p)` |

---

## 三、旧项目兼容策略（核心诉求）

### 3.1 场景定义

> 旧项目使用 Shiro 或 Spring Security，新项目全部用 sa-token，但旧项目要**逐步接入 ddd4j**。

### 3.2 策略：双写期 + 桥接层（非侵入式迁移）

```
旧项目现状：
  Shiro Realm + Session  ──→  业务代码直接调 SecurityUtils.getSubject()

接入 ddd4j 后（过渡期）：
  Shiro Realm + Session
       │
       ▼
  ShiroSubjectProvider（ddd4j-auth-shiro）
       │ 委托
       ▼
  Shiro SecurityUtils.getSubject()
       │
       ▼
  业务代码改调 SubjectKit.getSubject()（统一入口）
       │
       ▼  后续切换实现
  SaTokenSubjectProvider（ddd4j-auth-satoken）

最终态：
  sa-token + StpUtil  ──→  SubjectKit.getSubject()（无感知切换）
```

### 3.3 兼容实现的 3 个关键点

#### 关键点 1：`ShiroSubject` 必须完整（当前已 70%）

补全 `getPrincipalByLoginId` / `getPrincipalByToken`：

```java
@Override
public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
    // 通过 Shiro Session DAO 反查
    SessionDAO sessionDAO = ...; // 注入
    Collection<Session> sessions = sessionDAO.getActiveSessions();
    for (Session session : sessions) {
        if (Objects.equals(session.getAttribute("loginId"), loginId)) {
            return (T) session.getAttribute("principal");
        }
    }
    return null;
}
```

#### 关键点 2：`SecuritySubject` 必须重写（当前 0%）

```java
@Override
public <T extends AuthPrincipal> T getPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) return null;
    Object principal = authentication.getPrincipal();
    return (principal instanceof AuthPrincipal) ? (T) principal : null;
}

@Override
public boolean isPermitted(String permission) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return false;
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(permission::equals);
}
```

#### 关键点 3：`SaTokenSubject.getPrincipal()` 必须实现（当前 return null）

```java
@Override
public <T extends AuthPrincipal> T getPrincipal() {
    if (!StpUtil.isLogin()) return null;
    Object loginId = StpUtil.getLoginId();
    // 从 StpUtil.getSession().get("principal") 取（登录时存入）
    return (T) StpUtil.getSession().get("principal");
}
```

### 3.4 旧项目迁移路径（4 阶段）

| 阶段                     | 动作                                                                               | 风险 | 可回滚           |
|------------------------|----------------------------------------------------------------------------------|----|---------------|
| **阶段 1：接入 ddd4j-core** | 引入 `ddd4j-auth-core` + 对应 `auth-shiro/security`，业务代码改用 `SubjectKit.getSubject()` | 低  | ✅ 仅改调用方式      |
| **阶段 2：双写运行**          | 同时启用 Shiro SubjectProvider，旧 `SecurityUtils.getSubject()` 仍可用                    | 中  | ✅ 删除依赖即回滚     |
| **阶段 3：灰度切换**          | 试点服务引入 `ddd4j-auth-satoken`，新接口用 sa-token，旧接口保留 Shiro                            | 中  | ✅ 注解区分        |
| **阶段 4：全量切换**          | 移除 Shiro 依赖，统一 sa-token                                                          | 高  | ⚠️ 需重新发 Token |

---

## 四、落地任务清单（按优先级）

### P0：必须完成（否则 auth 模块不可用）

| # | 任务                                      | 文件                                      | 说明                                                            |
|---|-----------------------------------------|-----------------------------------------|---------------------------------------------------------------|
| 1 | **修复 SubjectKit 注册断链**                  | 新增 `ddd4j-auth-spring/SubjectRegistrar` | 把 `SubjectProvider` Bean 写回 `SubjectKit.subjectProvider`      |
| 2 | **实现 SaTokenSubject.getPrincipal()**    | `SaTokenSubject.java`                   | 从 `StpUtil.getSession().get("principal")` 取                   |
| 3 | **重写 SecuritySubject**                  | `SecuritySubject.java`                  | 委托 `SecurityContextHolder`，当前全是空壳                             |
| 4 | **补全 ShiroSubject 的 ByLoginId/ByToken** | `ShiroSubject.java`                     | 通过 SessionDAO 反查                                              |
| 5 | **统一自动配置注册**                            | 4 个 `*.imports` 文件                      | satoken/security/shiro/license 都补 `AutoConfiguration.imports` |

### P1：模块重组（对齐 mq 范式）

| # | 任务                                          | 说明                                                           |
|---|---------------------------------------------|--------------------------------------------------------------|
| 6 | 新增 `ddd4j-auth-core` 纯 Java SPI 模块          | 从 ddd4j-core 迁入 Subject/SubjectKit/SubjectProvider 体系（含会话能力） |
| 7 | 新增 `ddd4j-auth-spring` Spring 桥接模块          | `SubjectRegistrar` + 自动装配                                    |
| 8 | `ddd4j-auth-datascope` 迁移到 `ddd4j-data`     | 数据权限不属于鉴权                                                    |
| 9 | `ddd4j-auth-license` 迁移到 `ddd4j-extensions` | 软件授权不属于鉴权                                                    |

### P2：能力增强（借鉴 Sa-Token）

| #  | 任务                                     | 说明                                                                              |
|----|----------------------------------------|---------------------------------------------------------------------------------|
| 10 | 扩展 `Subject` 接口会话能力                    | 新增 `login/logout/kickout/refresh/verify/disable/setAttribute`（对齐 StpLogic 能力边界） |
| 11 | 新增 `AuthRequest` 登录请求载体                | 纯 Java 值对象，承载 loginId/principal/timeout/device/realm/extra                      |
| 12 | **新增 `SubjectDataProvider` 权限数据源 SPI** | 对齐 Sa-Token `StpInterface`；框架不持有权限数据，业务提供 getPermissionList/getRoleList         |
| 13 | **新增 `SubjectStrategy` 函数式策略集**        | 对齐 Sa-Token `SaStrategy`；createToken/hasElement/isExpired/createSubject 可热替换    |
| 14 | `SubjectKit` 扩展为全局注册中心                 | 对齐 `SaManager`；register SubjectProvider + setDataProvider + getStrategy + 默认兜底  |
| 15 | `SubjectProvider` 支持多账号体系              | 新增 `getSubject(realm)` 默认方法，对齐 `SaManager.getStpLogic(loginType)`               |
| 16 | 统一异常映射                                 | sa-token/SpringSecurity/Shiro 异常 → ddd4j `ServiceException`                     |

### P3：文档与示例

| #  | 任务        | 说明                                            |
|----|-----------|-----------------------------------------------|
| 14 | 编写迁移指南    | Shiro → sa-token / SpringSecurity → sa-token  |
| 15 | 提供三鉴权示例工程 | `ddd4j-samples/auth-{shiro,security,satoken}` |

---

## 五、模块依赖图（重构后）

```
                ┌─────────────────────────────┐
                │      业务应用代码            │
                │   SubjectKit.getSubject()   │
                │   SubjectKit.getPrincipal() │
                └──────────────┬──────────────┘
                               │
                ┌──────────────▼──────────────┐
                │     ddd4j-auth-core          │ ← 纯 Java SPI
                │   Subject / SubjectProvider  │
                │   SubjectKit / AuthPrincipal │
                │   AuthRequest                │
                └──────────────┬──────────────┘
                               │
           ┌───────────────────┼───────────────────┐
           ▼                   ▼                   ▼
  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
  │ ddd4j-auth-    │  │ ddd4j-auth-    │  │ ddd4j-auth-    │
  │ satoken        │  │ security       │  │ shiro          │
  │ (主推)          │  │ (兼容旧SS)     │  │ (兼容旧Shiro)  │
  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘
          │                   │                   │
          └───────────────────┼───────────────────┘
                              ▼
                 ┌────────────────────────┐
                 │  ddd4j-auth-spring     │ ← Spring 桥接
                 │  SubjectRegistrar      │
                 │  AuthAutoConfiguration │
                 └────────────────────────┘
```

---

## 六、验收标准

### 6.1 编译期

```bash
cd ddd4j-auth && mvn clean compile -pl ddd4j-auth-core,ddd4j-auth-satoken,ddd4j-auth-security,ddd4j-auth-shiro,ddd4j-auth-spring
# 必须全部 BUILD SUCCESS
```

### 6.2 运行期（最小可用验证）

| 验证项                                               | 方法                                                          |
|---------------------------------------------------|-------------------------------------------------------------|
| `SubjectKit.register()` 可用                        | 单元测试调用后 `getSubject()` 不抛异常                                 |
| sa-token 登录后 `SubjectKit.getPrincipal()` 返回非 null | 集成测试                                                        |
| Shiro 适配不破坏旧代码                                    | 旧项目引入 `ddd4j-auth-shiro` 后 `SecurityUtils.getSubject()` 仍可用 |
| 三实现互斥可切换                                          | 通过 `@ConditionalOnClass` 自动选择，不冲突                           |

### 6.3 ArchUnit 守护

```java
@ArchTest
static final ArchRule auth_core_no_spring =
    noClasses().that().resideInAPackage("io.ddd4j.auth.core..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework..");

@ArchTest
static final ArchRule auth_core_no_sa_token =
    noClasses().that().resideInAPackage("io.ddd4j.auth.core..")
        .should().dependOnClassesThat().resideInAPackage("cn.dev33..");
```

---

## 七、总结

**当前 `ddd4j-auth` 模块不可用的根本原因**：`SubjectKit.subjectProvider` 静态字段从未被任何代码写入，导致
`SubjectKit.getSubject()` 永远抛异常。其次是 `SaTokenSubject` / `SecuritySubject` 是空壳实现。

**最小修复路径（1 天可完成）**：

1. 修复 SubjectKit 注册断链（新增 `SubjectRegistrar`）
2. 实现 `SaTokenSubject.getPrincipal()`
3. 重写 `SecuritySubject`
4. 补全 4 个自动配置 imports

**完整达标路径（1 周可完成）**：

1. 拆分 `ddd4j-auth-core`（纯 Java SPI）+ `ddd4j-auth-spring`（桥接）
2. 迁出 `datascope` / `license`
3. 扩展 `Subject` 接口会话能力（login/logout/refresh/verify）+ 新增 `AuthRequest`
4. 三鉴权实现完整化 + 互斥自动装配

**旧项目兼容策略**：通过 `SubjectKit` 统一入口，过渡期 Shiro/Security 与 sa-token 并存，业务代码改为调
`SubjectKit.getSubject()`，后续切换实现零代码改动。

---

## 八、修订：框架独立性约束（关键，对齐 ddd4j-mq 范式）

### 8.1 约束来源与原则

用户明确要求：

> - `ddd4j-auth-security` **可以依赖 Spring**（因为 Spring Security 本身就是 Spring 生态）
> - `ddd4j-auth-shiro` 和 `ddd4j-auth-satoken` **必须保持框架无关**（纯 Java），以便在 `ddd4j-boot`、`ddd4j-javalin`、
    `ddd4j-quarkus` 三个下游项目都能使用
> - 与三下游项目的**深度整合写在各自的整合模块里**（如 `ddd4j-boot/.../ddd4j-boot-auth-satoken`），而非写在通用脚手架里

这与 `ddd4j-mq` 的拆分范式完全一致：

```
ddd4j-mq（已达标范式）：
├── ddd4j-mq-core/          ← 纯 Java SPI（零 Spring）
├── ddd4j-mq-kafka/         ← 纯 Java 实现（依赖 spring-kafka 但 @ConditionalOnClass 在 -spring 模块）
├── ddd4j-mq-spring/        ← Spring 桥接（注册 MQBrokerAdapter Bean）
└── 与下游整合：ddd4j-spring/ddd4j-quarkus 各自提供
```

### 8.2 当前 Spring 污染点扫描结果

| 模块                    | Spring 污染点                                                                                                                                                                                                                 | 处理方式                                  |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------|
| `ddd4j-auth-satoken`  | `SaTokenEnhanceAutoConfiguration`（@Configuration/@Bean）<br>`SaTokenExceptionHandler`（@ControllerAdvice/@ExceptionHandler，依赖 spring-web）<br>`SaMixCheckLoginHandler`、`SaTempKit`（依赖 `org.springframework.util.StringUtils`） | **全部迁出**到下游 Spring 整合模块               |
| `ddd4j-auth-shiro`    | `WebShiroBizConfiguration`（@Configuration/@Bean）                                                                                                                                                                           | **迁出**到下游 Spring 整合模块                 |
| `ddd4j-auth-security` | 大量 Spring Security/JWT/Redis 依赖                                                                                                                                                                                            | **保留**（Spring Security 本就是 Spring 组件） |

### 8.3 修订后的模块拓扑

```
ddd4j-auth/（通用脚手架，框架无关）
├── ddd4j-auth-core/                ← 【新增】纯 Java SPI（零 Spring）
│   ├── subject/
│   │   ├── Subject.java            ← 核心契约（读取+校验+会话操作 login/logout/refresh/verify）
│   │   ├── SubjectProvider.java    ← Subject 工厂 SPI
│   │   └── AuthPrincipal.java
│   ├── SubjectKit.java             ← 静态门面（业务统一入口）
│   ├── AuthRequest.java            ← 登录请求载体
│   └── AuthErrorCode.java
│
├── ddd4j-auth-satoken/             ← 【重构】纯 Java 实现（零 Spring）
│   ├── subject/
│   │   ├── SaTokenSubject.java     ← 完整实现 getPrincipal + login/logout/refresh/verify
│   │   └── SaTokenSubjectProvider.java
│   ├── annotation/
│   │   ├── SaAdminCheckLogin.java  ← 纯注解（去掉 @SaCheckLogin 元注解依赖，运行时由 sa-token 识别）
│   │   └── SaMixCheckLogin.java
│   └── util/
│       ├── StpKit.java             ← 替换 StringUtils.hasText → 纯 Java
│       └── SaTempKit.java          ← 替换 StringUtils.hasText → 纯 Java
│   （移出：SaTokenEnhanceAutoConfiguration / SaTokenExceptionHandler）
│
├── ddd4j-auth-shiro/               ← 【重构】纯 Java 实现（零 Spring）
│   ├── subject/
│   │   ├── ShiroSubject.java       ← 补全 login/logout（已有 getPrincipal）
│   │   └── ShiroSubjectProvider.java
│   （移出：WebShiroBizConfiguration）
│
└── ddd4j-auth-security/            ← 【保留 Spring 依赖】（Spring Security 原生）
    ├── subject/
    │   ├── SecuritySubject.java    ← 重写空壳，委托 SecurityContextHolder + login/logout
    │   └── SecuritySubjectProvider.java
    ├── jwt/                        ← 保留全部 JWT 实现
    ├── WebSecurityBizConfiguration.java
    └── WebSecurityJwtConfiguration.java

【Spring 桥接下沉到 ddd4j-spring 或下游项目】
ddd4j-spring/ddd4j-spring-auth/     ← 【新增】Spring 通用桥接
├── SubjectRegistrar.java           ← BeanPostProcessor 把 SubjectProvider 写回 SubjectKit
├── AuthSpringAutoConfiguration.java
└── satoken/
    └── SaTokenSpringAutoConfiguration.java  ← 从 ddd4j-auth-satoken 迁入
    └── SaTokenExceptionHandler.java         ← 从 ddd4j-auth-satoken 迁入

【与下游项目深度整合写在各自项目】
ddd4j-boot/.../ddd4j-boot-auth-satoken/      ← Spring Boot + sa-token 整合
ddd4j-boot/.../ddd4j-boot-auth-security/     ← Spring Boot + Spring Security 整合
ddd4j-quarkus/.../ddd4j-quarkus-auth-satoken/← Quarkus + sa-token 整合
ddd4j-javalin/.../ddd4j-javalin-auth-satoken/← Javalin + sa-token 整合
```

### 8.4 三种实现 × 三种容器的整合矩阵

|                     | ddd4j-boot (Spring)                                                   | ddd4j-quarkus (CDI)                                                  | ddd4j-javalin (Guice)                                                       |
|---------------------|-----------------------------------------------------------------------|----------------------------------------------------------------------|-----------------------------------------------------------------------------|
| **sa-token**        | `ddd4j-boot-auth-satoken`<br>（含 SaTokenExceptionHandler + AutoConfig） | `ddd4j-quarkus-auth-satoken`<br>（CDI Bean + Quarkus ExceptionMapper） | `ddd4j-javalin-auth-satoken`<br>（Guice Provider + Javalin ExceptionHandler） |
| **Spring Security** | `ddd4j-boot-auth-security`<br>（Spring 原生，仅 Spring 可用）                 | ❌ 不适用                                                                | ❌ 不适用                                                                       |
| **Shiro**           | `ddd4j-boot-auth-shiro`<br>（WebShiroBizConfiguration）                 | `ddd4j-quarkus-auth-shiro`<br>（CDI + Shiro Quarkus 扩展）               | `ddd4j-javalin-auth-shiro`<br>（Guice + Shiro Javalin 集成）                    |

**底层共享**（三容器通用，纯 Java）：

- `ddd4j-auth-core`（Subject / SubjectKit / SubjectProvider / AuthRequest / AuthPrincipal）
- `ddd4j-auth-satoken`（SaTokenSubject 完整契约 + StpKit）
- `ddd4j-auth-shiro`（ShiroSubject 完整契约）

### 8.5 sa-token Spring 桥接的迁移细节

#### 需要从 `ddd4j-auth-satoken` 迁出的文件

| 文件                                                | 迁入目标                                            | 原因                                                      |
|---------------------------------------------------|-------------------------------------------------|---------------------------------------------------------|
| `SaTokenEnhanceAutoConfiguration.java`            | `ddd4j-spring-auth` 或 `ddd4j-boot-auth-satoken` | 依赖 `@Configuration`/`@Bean`/`InitializingBean`          |
| `SaTokenExceptionHandler.java`                    | `ddd4j-boot-auth-satoken`                       | 依赖 `@ControllerAdvice`/`@ExceptionHandler`/`spring-web` |
| `SaMixCheckLoginHandler` 中的 `StringUtils.hasText` | 替换为纯 Java `!str.isBlank()`                      | `org.springframework.util.StringUtils`                  |

#### `ddd4j-auth-satoken` 重构后 pom.xml

```xml
<dependencies>
    <dependency>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-auth-core</artifactId>
        <version>${revision}</version>
    </dependency>
    <!-- Sa-Token 核心（纯 Java，零 Spring） -->
    <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-core</artifactId>
    </dependency>
    <!-- Sa-Token API Key -->
    <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-apikey</artifactId>
    </dependency>
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
<!-- 注意：不再依赖 spring-web / hutool-all -->
```

#### `ddd4j-auth-shiro` 重构后 pom.xml

```xml
<dependencies>
    <dependency>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-auth-core</artifactId>
        <version>${revision}</version>
    </dependency>
    <!-- Shiro 核心（纯 Java，零 Spring） -->
    <dependency>
        <groupId>org.apache.shiro</groupId>
        <artifactId>shiro-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.shiro</groupId>
        <artifactId>shiro-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.github.hiwepy</groupId>
        <artifactId>shiro-biz</artifactId>
    </dependency>
</dependencies>
<!-- 注意：不再有任何 spring-context 依赖 -->
<!-- WebShiroBizConfiguration 迁出到 ddd4j-boot-auth-shiro -->
```

### 8.6 修订后的依赖图

```
┌─────────────────────────────────────────────────────────────────────┐
│  业务应用代码（SubjectKit.getSubject() 统一入口）                      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                ┌──────────────▼──────────────┐
                │     ddd4j-auth-core          │ 纯 Java SPI
                │   Subject / SubjectKit       │ （零框架依赖）
                │   SubjectProvider / AuthReq  │
                └──────────────┬──────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
┌────────────────┐   ┌────────────────┐   ┌────────────────────┐
│ ddd4j-auth-    │   │ ddd4j-auth-    │   │ ddd4j-auth-security │
│ satoken        │   │ shiro          │   │ （Spring 专属）      │
│ ★ 纯 Java ★    │   │ ★ 纯 Java ★    │   │ @Configuration     │
│ SaTokenSubject │   │ ShiroSubject   │   │ SecuritySubject    │
│ StpUtil.login  │   │ SecurityUtils  │   │ + JWT/Redis        │
└───────┬────────┘   └───────┬────────┘   └─────────┬──────────┘
        │                    │                       │
        │  + Spring 桥接      │  + Spring 桥接         │ （已含）
        ▼                    ▼                       │
┌──────────────────────────────────┐                 │
│  ddd4j-spring-auth / 下游项目      │                 │
│  SubjectRegistrar（注册到Kit）     │                 │
│  SaTokenSpringAutoConfiguration   │                 │
│  SaTokenExceptionHandler          │                 │
│  WebShiroBizConfiguration         │                 │
└──────────────────────────────────┘                 │
        │                                            │
        ▼                                            ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│ ddd4j-boot      │  │ ddd4j-quarkus   │  │ ddd4j-javalin       │
│ -auth-satoken   │  │ -auth-satoken   │  │ -auth-satoken       │
│ -auth-shiro     │  │ -auth-shiro     │  │ -auth-shiro         │
│ -auth-security  │  │ （CDI 整合）     │  │ （Guice 整合）       │
└─────────────────┘  └─────────────────┘  └─────────────────────┘
```

### 8.7 修订后的落地任务（替换第四章）

#### P0：核心修复（让 auth 可用）

| # | 任务                                     | 文件                                         | 说明                                          |
|---|----------------------------------------|--------------------------------------------|---------------------------------------------|
| 1 | **新增 `SubjectKit.register()` 方法**      | `ddd4j-auth-core/SubjectKit.java`          | 暴露静态注册入口                                    |
| 2 | **实现 `SaTokenSubject.getPrincipal()`** | `ddd4j-auth-satoken/SaTokenSubject.java`   | 从 `StpUtil.getSession().get("principal")` 取 |
| 3 | **重写 `SecuritySubject`**               | `ddd4j-auth-security/SecuritySubject.java` | 委托 `SecurityContextHolder`                  |
| 4 | **补全 `ShiroSubject`**                  | `ddd4j-auth-shiro/ShiroSubject.java`       | 通过 SessionDAO 反查 ByLoginId                  |

#### P1：框架解耦（satoken/shiro 去 Spring 化）

| # | 任务                                   | 说明                                                                                                                         |
|---|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| 5 | **`ddd4j-auth-satoken` 去 Spring 化**  | 移除 `spring-web` 依赖；迁出 `SaTokenEnhanceAutoConfiguration`/`SaTokenExceptionHandler`；`StringUtils.hasText` → `!str.isBlank()` |
| 6 | **`ddd4j-auth-shiro` 去 Spring 化**    | 迁出 `WebShiroBizConfiguration`                                                                                              |
| 7 | **新增 `ddd4j-auth-core` 纯 Java SPI**  | 从 ddd4j-core 迁入 Subject/SubjectKit/SubjectProvider；扩展 Subject 会话能力（login/logout/refresh/verify）；新增 AuthRequest             |
| 8 | **新增 `ddd4j-spring-auth` Spring 桥接** | `SubjectRegistrar`（BeanPostProcessor）+ 从 satoken/shiro 迁入的 AutoConfiguration                                               |

#### P2：下游整合（三容器各自实现）

| #  | 任务                                | 说明                                                                                     | 状态    |
|----|-----------------------------------|----------------------------------------------------------------------------------------|-------|
| 9  | **`ddd4j-boot-auth-satoken`**     | Spring Boot + sa-token 深度整合（SaTokenEnhanceAutoConfiguration + SaTokenExceptionHandler） | ✅ 已交付 |
| 10 | **`ddd4j-boot-auth-security`**    | Spring Boot + Spring Security 整合（SecurityEnhanceAutoConfiguration + PasswordEncoder）   | ✅ 已交付 |
| 11 | **`ddd4j-boot-auth-shiro`**       | Spring Boot + Shiro 整合（ShiroEnhanceAutoConfiguration，含迁出的 WebShiroBizConfiguration）    | ✅ 已交付 |
| 12 | **`ddd4j-quarkus-auth-satoken`**  | Quarkus + sa-token 整合（CDI Bean + ExceptionMapper）                                      | ✅ 已交付 |
| 13 | **`ddd4j-quarkus-auth-shiro`**    | Quarkus + Shiro 整合（CDI Bean + Shiro ExceptionMapper）                                   | ✅ 已交付 |
| 14 | **`ddd4j-quarkus-auth-security`** | Quarkus + Spring Security 整合（CDI Bean，兼容选项）                                            | ✅ 已交付 |
| 15 | **`ddd4j-javalin-auth-satoken`**  | Javalin + sa-token 整合（Guice Module + Javalin ExceptionHandler）                         | ✅ 已交付 |
| 16 | **`ddd4j-javalin-auth-shiro`**    | Javalin + Shiro 整合（Guice Module + Javalin ExceptionHandler）                            | ✅ 已交付 |
| 17 | **`ddd4j-javalin-auth-security`** | Javalin + Spring Security 整合（Guice Module，兼容选项）                                        | ✅ 已交付 |

> **交付完整性**：三容器 × 三鉴权 = **9 个整合模块全部交付**。
> - **ddd4j-boot**（Spring Boot）：`ddd4j-boot-auth/` 下 3 个子模块，Spring @AutoConfiguration 风格
> - **ddd4j-quarkus**（CDI）：`ddd4j-quarkus-auth/` 下 3 个子模块，CDI @ApplicationScoped + JAX-RS ExceptionMapper 风格
> - **ddd4j-javalin**（Guice）：`ddd4j-javalin-auth/` 下 3 个子模块，Guice AbstractModule + Javalin Handler 风格

#### P3：模块归属调整

| #  | 任务                                            | 说明        |
|----|-----------------------------------------------|-----------|
| 16 | **`ddd4j-auth-datascope` → `ddd4j-data`**     | 数据权限不属于鉴权 |
| 17 | **`ddd4j-auth-license` → `ddd4j-extensions`** | 软件授权不属于鉴权 |

### 8.8 修订后的验收标准

```java
// ArchUnit 守护规则（新增框架独立性检查）
@ArchTest
static final ArchRule auth_satoken_no_spring =
    noClasses().that().resideInAPackage("io.ddd4j.auth.satoken..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework..");

@ArchTest
static final ArchRule auth_shiro_no_spring =
    noClasses().that().resideInAPackage("io.ddd4j.auth.shiro..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework..");

// security 模块允许 Spring（白名单）
// auth-core 纯 Java
@ArchTest
static final ArchRule auth_core_no_framework =
    noClasses().that().resideInAPackage("io.ddd4j.auth.core..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "cn.dev33..", "org.apache.shiro..");
```

```bash
# 三容器可复用性验证
cd ddd4j-auth/ddd4j-auth-satoken && mvn dependency:tree | grep spring
# 必须输出：(无任何 spring 依赖)

cd ddd4j-auth/ddd4j-auth-shiro && mvn dependency:tree | grep spring
# 必须输出：(无任何 spring 依赖)

# Quarkus/Javalin 可直接依赖这两个模块
cd ddd4j-quarkus && mvn dependency:tree | grep ddd4j-auth-satoken
# 必须输出：io.ddd4j:ddd4j-auth-satoken
```

---

## 九、借鉴 Sa-Token 反哺 Subject 三件套设计（核心）

> **背景**：Sa-Token（18,045 符号 / 48,517 边）被公认为最轻巧灵活的鉴权框架。`ddd4j-auth` 的存在价值就是屏蔽 sa-token /
> shiro / security 三者差异。本节提炼 Sa-Token 的核心设计精髓，反哺 ddd4j-auth 的 `Subject` / `SubjectKit` /
`SubjectProvider` 三件套，使其在抽象层级上达到同等成熟度。

### 9.1 Sa-Token 五大设计精髓（codegraph 提炼）

#### 精髓 1：`StpLogic` — 单一职责的领域逻辑核心

```java
public class StpLogic {
    public String loginType;                          // 账号类型（多账号体系隔离）
    private SaTokenConfig config;                     // 私有配置（可覆盖全局）

    // 2800+ 行核心逻辑全部集中在此类
    public void login(Object id, SaLoginParameter p) {...}
    public void logout() {...}
    public void kickout(Object loginId) {...}
    public SaSession getSession() {...}
    public boolean hasPermission(String p) {...}
    // ... 130+ 个方法
}
```

**精髓**：**一个类承载一种账号体系的全部能力**。登录/登出/会话/权限/角色/封禁/设备/切换……全部内聚，不向外拆分。

#### 精髓 2：`StpUtil` — 静态门面 + 默认实例

```java
public class StpUtil {
    public static final String TYPE = "login";
    public static StpLogic stpLogic = new StpLogic(TYPE);   // 默认实例

    // 所有方法都是对 stpLogic 的委托
    public static void login(Object id) {
        stpLogic.login(id);
    }
    public static Object getLoginId() {
        return stpLogic.getLoginId();
    }
    // ...
}
```

**精髓**：**静态门面 + 可替换默认实例**。业务代码调 `StpUtil.xxx()` 最简，需要多账号体系时调 `StpUtil.stpLogic.login(...)`
或 `new StpLogic("admin")`。

#### 精髓 3：`SaManager` — 全局静态注册中心（SPI 装配点）

```java
public class SaManager {
    public volatile static SaTokenConfig config;
    private volatile static SaTokenDao saTokenDao;           // 持久化 SPI
    private volatile static SaTokenContext saTokenContext;   // 上下文 SPI（请求/响应/存储）
    private volatile static StpInterface stpInterface;       // 权限/角色数据源 SPI
    private volatile static SaJsonTemplate saJsonTemplate;   // JSON SPI
    // ... 全部用 volatile + 双重检查锁 + 默认实现兜底

    public static void setStpInterface(StpInterface s) {...}
    public static StpInterface getStpInterface() {
        if (stpInterface == null) {
            synchronized (SaManager.class) {
                if (stpInterface == null) {
                    stpInterface = new StpInterfaceDefaultImpl();  // 默认空实现兜底
                }
            }
        }
        return stpInterface;
    }
    // 多账号体系注册表
    public static StpLogic getStpLogic(String loginType, boolean isCreate) {...}
}
```

**精髓**：**每个 SPI 都有 `set/get` 静态对 + 默认实现兜底 + 全局事件通知**。框架无关，Spring/Quarkus/Javalin 各自在启动时
`SaManager.setXxx()` 注入实现。

#### 精髓 4：`StpInterface` — 权限数据源 SPI（解耦业务）

```java
public interface StpInterface {
    List<String> getPermissionList(Object loginId, String loginType);
    List<String> getRoleList(Object loginId, String loginType);
    default SaDisableWrapperInfo isDisabled(Object loginId, String service) {...}
}
```

**精髓**：**框架不持有权限数据，业务实现 `StpInterface` 提供数据源**。Sa-Token 只负责"校验"，不负责"存储权限"。这是
ddd4j-auth 应该学的关键解耦点。

#### 精髓 5：`SaStrategy` — 函数式策略可替换

```java
public class SaStrategy {
    public static final SaStrategy instance = new SaStrategy();

    public SaCreateTokenFunction createToken;          // Token 生成策略
    public SaCreateSessionFunction createSession;      // Session 创建策略
    public SaHasElementFunction hasElement;            // 权限匹配策略
    public SaGenerateUniqueTokenFunction generateUniqueToken;
    public SaCreateStpLogicFunction createStpLogic;    // StpLogic 创建策略
    public SaAutoRenewFunction autoRenew;              // 自动续期策略
}
```

**精髓**：**核心行为全部做成 `Function` 字段，业务可热替换**。例如
`SaStrategy.instance.hasElement = (list, element) -> list.contains(element)`。

### 9.2 ddd4j-auth 三件套的对照映射

| Sa-Token 设计                | ddd4j-auth 对应设计               | 借鉴要点                                                                                       |
|----------------------------|-------------------------------|--------------------------------------------------------------------------------------------|
| `StpLogic`（2800 行单一职责）     | `Subject` 接口                  | 把 sa-token 的 login/logout/kickout/session/permission **全部收敛到 Subject**，而非另立 Authentication |
| `StpUtil`（静态门面 + 默认实例）     | `SubjectKit`                  | 静态门面 + `register(SubjectProvider)` 注入实现；业务调 `SubjectKit.login(req)` 最简                     |
| `SaManager`（全局注册中心 + 默认兜底） | `SubjectKit` 内部静态字段           | volatile + 双重检查锁 + 默认兜底；`SubjectKit.subjectProvider` 由适配层 `register()`                     |
| `StpInterface`（权限数据源 SPI）  | **【新增】`SubjectDataProvider`** | ddd4j 不持有权限数据，业务实现此 SPI 提供权限/角色源                                                           |
| `SaStrategy`（函数式策略）        | **【新增】`SubjectStrategy`**     | Token 生成/匹配/会话创建等核心行为做成可替换 Function                                                        |
| `SaSession`（会话数据容器）        | `AuthPrincipal`（已有）           | 登录后存入 Session/Token Claim，通过 `Subject.getPrincipal()` 取回                                   |
| `loginType`（多账号体系）         | `AuthRequest.realm`           | 多账号体系标识，sa-token→StpLogic("admin")，shiro→多 Realm，security→多 SecurityContext                |

### 9.3 反哺后的完整契约设计（修订 2.2 节）

#### 9.3.1 `Subject` 接口（核心，对齐 StpLogic 能力边界）

```java
public interface Subject {

    // ============ 身份与会话读取（现有）============
    <T extends AuthPrincipal> T getPrincipal();
    <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId);
    <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue);
    boolean isAuthenticated();
    Object getLoginId();
    // ... 其余 default 方法保留

    // ============ 会话生命周期（原 StpLogic.login/logout/kickout）============
    String login(AuthRequest request);
    void logout();
    void logout(Object loginId);
    void kickout(Object loginId);              // 踢人下线（区别于 logout：被踢方收到踢出事件）
    String refresh();                          // 续期/换发凭证
    <T extends AuthPrincipal> T verify(String token);  // 仅校验不建会话

    // ============ 权限与角色（委托 SubjectDataProvider）============
    boolean isPermitted(String permission);
    boolean hasRole(String roleIdentifier);
    boolean[] isPermitted(String... permissions);
    // ... 现有契约保留

    // ============ 会话数据操作（原 SaSession.setData/getData）============
    default void setAttribute(String key, Object value) {
        getPrincipal().getProfile().put(key, value);
    }
    default <V> V getAttribute(String key) {
        return (V) getPrincipal().getProfile().get(key);
    }

    // ============ 封禁（原 StpLogic.disable）============
    void disable(Object loginId, long timeout);
    boolean isDisabled(Object loginId);
    void untieDisable(Object loginId);
}
```

#### 9.3.2 `SubjectKit` 静态门面（对齐 StpUtil + SaManager）

```java
public final class SubjectKit {

    // ====== 全局注册中心（对齐 SaManager）======
    static volatile SubjectProvider subjectProvider;
    static volatile SubjectDataProvider dataProvider;       // 【新增】权限数据源
    static volatile SubjectStrategy strategy;                // 【新增】策略集

    /** 注册 Subject 工厂（框架适配层调用） */
    public static void register(SubjectProvider provider) {
        subjectProvider = provider;
    }

    /** 注册权限数据源（业务调用，对齐 StpInterface） */
    public static void setDataProvider(SubjectDataProvider provider) {
        dataProvider = provider;
    }

    /** 获取当前 Subject（对齐 StpUtil.stpLogic） */
    public static Subject getSubject() {
        if (subjectProvider == null) {
            synchronized (SubjectKit.class) {
                if (subjectProvider == null) {
                    throw new IllegalStateException("SubjectProvider not registered.");
                }
            }
        }
        return subjectProvider.getSubject();
    }

    public static SubjectDataProvider getDataProvider() {
        if (dataProvider == null) {
            synchronized (SubjectKit.class) {
                if (dataProvider == null) {
                    dataProvider = SubjectDataProvider.DEFAULT;  // 默认空实现兜底
                }
            }
        }
        return dataProvider;
    }

    public static SubjectStrategy getStrategy() {
        if (strategy == null) {
            synchronized (SubjectKit.class) {
                if (strategy == null) {
                    strategy = SubjectStrategy.instance;  // 默认策略
                }
            }
        }
        return strategy;
    }

    // ====== 便捷门面（对齐 StpUtil 静态方法）======
    public static String login(AuthRequest request) { return getSubject().login(request); }
    public static void logout() { getSubject().logout(); }
    public static void logout(Object loginId) { getSubject().logout(loginId); }
    public static void kickout(Object loginId) { getSubject().kickout(loginId); }
    public static String refresh() { return getSubject().refresh(); }
    public static <T extends AuthPrincipal> T getPrincipal() { return getSubject().getPrincipal(); }
    public static boolean isLogin() { return getSubject().isAuthenticated(); }
    public static boolean hasPermission(String p) { return getSubject().isPermitted(p); }
    public static boolean hasRole(String r) { return getSubject().hasRole(r); }
}
```

#### 9.3.3 【新增】`SubjectDataProvider` 权限数据源 SPI（对齐 StpInterface）

> **这是借鉴 Sa-Token 最关键的一点**：ddd4j-auth 框架本身**不持有权限数据**，由业务实现此 SPI 提供数据源。

```java
/**
 * 权限/角色数据源 SPI（对齐 Sa-Token 的 StpInterface）。
 * <p>框架不持有权限数据，业务实现此接口提供 getPermissionList / getRoleList。
 * <p>各鉴权实现（sa-token/shiro/security）在 isPermitted/hasRole 时委托此 SPI。
 */
public interface SubjectDataProvider {

    /** 默认空实现兜底（对齐 StpInterfaceDefaultImpl） */
    SubjectDataProvider DEFAULT = new SubjectDataProvider() {};

    /**
     * 返回指定账号拥有的权限码集合。
     * @param principal 认证主体
     * @return 权限码列表
     */
    default List<String> getPermissionList(AuthPrincipal principal) {
        return Collections.emptyList();
    }

    /**
     * 返回指定账号拥有的角色标识集合。
     * @param principal 认证主体
     * @return 角色列表
     */
    default List<String> getRoleList(AuthPrincipal principal) {
        return Collections.emptyList();
    }

    /**
     * 判断指定账号是否被封禁。
     * @param loginId 账号ID
     * @param service 业务标识
     */
    default boolean isDisabled(Object loginId, String service) {
        return false;
    }
}
```

#### 9.3.4 【新增】`SubjectStrategy` 函数式策略集（对齐 SaStrategy）

```java
/**
 * 核心行为策略集（对齐 Sa-Token 的 SaStrategy）。
 * <p>所有核心行为做成 Function 字段，业务可热替换。
 */
public class SubjectStrategy {

    public static final SubjectStrategy instance = new SubjectStrategy();

    /** Token 生成策略（对齐 SaCreateTokenFunction） */
    public Function<AuthRequest, String> createToken = AuthRequest::getLoginIdString;

    /** 权限匹配策略（对齐 SaHasElementFunction） */
    public BiFunction<List<String>, String, Boolean> hasElement = List::contains;

    /** 会话超时校验策略 */
    public Function<String, Boolean> isExpired = token -> false;

    /** 多账号体系 Subject 创建策略（对齐 SaCreateStpLogicFunction） */
    public Function<String, Subject> createSubject = realm -> SubjectKit.getSubject();
}
```

#### 9.3.5 `SubjectProvider` 工厂 SPI（现有，对齐多账号体系）

```java
public interface SubjectProvider {

    /**
     * 获取默认 Subject 实例。
     */
    Subject getSubject();

    /**
     * 【新增】按账号体系获取 Subject（对齐 SaManager.getStpLogic(loginType)）。
     * 默认实现忽略 realm，返回默认 Subject。
     */
    default Subject getSubject(String realm) {
        return getSubject();
    }
}
```

### 9.4 三鉴权实现如何落地新契约

| Subject 能力         | sa-token 实现                                              | Shiro 实现                                    | Spring Security 实现                                                      |
|--------------------|----------------------------------------------------------|---------------------------------------------|-------------------------------------------------------------------------|
| `login(req)`       | `StpUtil.login(loginId, SaLoginParameter)`               | `SecurityUtils.getSubject().login(token)`   | `SecurityContextHolder.setContext(...)`                                 |
| `logout()`         | `StpUtil.logout()`                                       | `Subject.logout()`                          | `SecurityContextHolder.clearContext()`                                  |
| `kickout(loginId)` | `StpUtil.kickout(loginId)`                               | `sessionDAO.delete(session)`                | 注销其 Session                                                             |
| `isPermitted(p)`   | 委托 `SubjectKit.getDataProvider().getPermissionList()` 比对 | 同（统一数据源）                                    | 同（统一数据源）                                                                |
| `getPrincipal()`   | `StpUtil.getSession().get("principal")`                  | `SecurityUtils.getSubject().getPrincipal()` | `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` |
| `disable(loginId)` | `StpUtil.disable(loginId, timeout)`                      | `cacheManager` 标记                           | 业务实现                                                                    |

**关键改进**：`isPermitted` / `hasRole` 不再各自从框架取权限，**统一委托 `SubjectKit.getDataProvider()`**
，这样三鉴权在权限校验上行为完全一致。

### 9.5 业务使用示例（对齐 Sa-Token 极简风格）

```java
// 1. 业务注册权限数据源（一次性，启动时）
SubjectKit.setDataProvider(new SubjectDataProvider() {
    @Override
    public List<String> getPermissionList(AuthPrincipal principal) {
        return permissionService.findByUserId(principal.getUserId());
    }
    @Override
    public List<String> getRoleList(AuthPrincipal principal) {
        return roleService.findByUserId(principal.getUserId());
    }
});

// 2. 登录（极简，对齐 StpUtil.login）
AuthPrincipal principal = new AuthPrincipal().setLoginId(10001).setUserId(20001);
String token = SubjectKit.login(AuthRequest.of(10001).principal(principal).timeout(7200));

// 3. 校验（极简，对齐 StpUtil.checkLogin）
SubjectKit.getSubject().checkLogin();              // 不登录抛异常
SubjectKit.hasPermission("user:add");              // 权限校验
SubjectKit.hasRole("admin");                       // 角色校验

// 4. 踢人下线
SubjectKit.kickout(10001);

// 5. 多账号体系（对齐 Sa-Token StpLogic("admin")）
Subject adminSubject = SubjectKit.getSubject("admin");
adminSubject.login(AuthRequest.of(adminId).realm("admin"));

// 6. 策略热替换（对齐 SaStrategy.instance.hasElement = ...）
SubjectKit.getStrategy().hasElement = (list, p) -> list.stream().anyMatch(p::matches);
```

### 9.6 借鉴总结：ddd4j-auth 的设计哲学

```
Sa-Token 的设计哲学：
  一个核心类（StpLogic）+ 一个门面（StpUtil）+ 一个注册中心（SaManager）
  + 数据源 SPI（StpInterface）+ 策略集（SaStrategy）
  → 极简调用、可热替换、框架无关

ddd4j-auth 的设计哲学（反哺后）：
  一个核心契约（Subject）+ 一个门面（SubjectKit）+ 一个工厂 SPI（SubjectProvider）
  + 数据源 SPI（SubjectDataProvider）+ 策略集（SubjectStrategy）
  → 同样极简调用、可热替换，且屏蔽 sa-token/shiro/security 三框架差异
```

**核心差异**：Sa-Token 是一个具体框架，`StpLogic` 是实现类；ddd4j-auth 是**屏蔽层**，`Subject` 是接口，底层可以是
sa-token、shiro 或 security 的任意实现。但抽象层级的设计理念完全对齐——这就是 ddd4j-auth 存在的意义。
