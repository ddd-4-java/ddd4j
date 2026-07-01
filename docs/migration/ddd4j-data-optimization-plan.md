# ddd4j-data 模块优化方案（三层范式版）

> **最后更新**：2026-07-01（基于当前平铺模块重新核实）
> **审计范围**：`ddd4j/ddd4j-data`（通用基础层，6 个子模块）
> **正确分层认知**：
> - `ddd4j/ddd4j-*` = **通用基础层**（纯 Java SPI + 可选 Spring 桥接，**不含 Spring Boot auto-config**）
> - `ddd4j-boot/ddd4j-boot-*` = **Spring Boot 整合层**（auto-config 放这里）
> - `ddd4j-quarkus/*` / `ddd4j-javalin/*` = **其他框架整合层**；`ddd4j` 内仅保留可复用的 `ddd4j-adapter-quarkus`、`ddd4j-adapter-guice` 与 `ddd4j-web-*` 通用适配
>
> **对照范本**：`ddd4j-mq`（`-core` 纯 Java + `-spring` Spring 桥接）→ `ddd4j-boot-mq`（Spring Boot auto-config）
> **核心问题**：`ddd4j-data` 通用层错误地混入了 Spring Boot auto-config 代码，应迁移到 `ddd4j-boot-data`

---

## 一、正确的三层范式（先对齐认知）

以 mq 模块为标准范本：

```
┌─────────────────────────────────────────────────────────────────┐
│ 第一层：ddd4j/ddd4j-mq-core（纯 Java SPI，零框架依赖）            │
│   contract/  spi/  registry/  serialization/                     │
│   → 任何框架可复用                                                │
├─────────────────────────────────────────────────────────────────┤
│ 第二层：ddd4j/ddd4j-mq-spring（Spring 桥接，@Configuration）     │
│   BeanPostProcessor / @Configuration / Message 转换              │
│   → 依赖 spring-context，不含 spring-boot-autoconfigure          │
├─────────────────────────────────────────────────────────────────┤
│ 第三层：ddd4j-boot/ddd4j-boot-mq（Spring Boot auto-config）      │
│   Ddd4jMQAutoConfiguration + AutoConfiguration.imports           │
│   → 依赖 spring-boot-autoconfigure，@ConditionalOnBean 装配      │
└─────────────────────────────────────────────────────────────────┘
```

**data 模块当前的错误**：第一层和第二层混在一起，且混入了本该在第三层的 Spring Boot auto-config。

---

## 二、ddd4j-data 现状（最新核实 2026-06-29）

### 2.1 子模块清单（6 个，含 Spring 桥接与 datascope）

| 子模块                      | 文件数 | 定位                                    |
|--------------------------|-----|---------------------------------------|
| ddd4j-data-mybatis       | 41  | MyBatis-Plus 实现                       |
| ddd4j-data-spring        | —   | Spring 桥接与仓储注册                       |
| ddd4j-data-crypto        | 18  | 加解密策略                                 |
| ddd4j-data-external      | 19  | 外部服务（geo/region/sequence/sys/weather） |
| ddd4j-data-logs          | 4   | API 操作日志（AspectJ）                     |
| **ddd4j-data-datascope** | 4   | **数据权限（新增，从 ddd4j-auth 迁入）**          |

### 2.2 Spring Boot auto-config 配置现状（逐项核实）

| 子模块           | metadata 文件内容                                                          | imports 文件                                                     | 配置类实际包名                   | 状态                                                       |
|---------------|------------------------------------------------------------------------|----------------------------------------------------------------|---------------------------|----------------------------------------------------------|
| **mybatis**   | `com.github.hiwepy.boot.autoconfigure.DefaultCryptoAutoConfiguration=` | ❌ 无                                                            | —                         | 🔴 错误（指向 crypto 的旧包名）                                    |
| **crypto**    | `io.ddd4j.data.crypto.DefaultCryptoAutoConfiguration=`                 | ❌ 无                                                            | `io.ddd4j.data.crypto`    | 🟡 metadata 对但缺 imports，auto-config 无法被发现                |
| **external**  | `io.ddd4j.data.external.ExternalAutoConfiguration=`                    | ✅ `io.ddd4j.data.external.ExternalAutoConfiguration`           | `io.ddd4j.data.external`  | 🟡 配置正确但**位置错**（应在 boot 层）                               |
| **logs**      | `com.github.hiwepy.boot.autoconfigure.ApiLogAspectConfiguration=`      | ❌ `io.hiwepy.boot.autoconfigure.ApiLogAspectConfiguration`（错误） | `io.ddd4j.data.logs`      | 🔴 全错（metadata + imports 都是旧包名）                          |
| **datascope** | `io.ddd4j.auth.datascope.DataScopeAutoConfiguration=`                  | ✅ `io.ddd4j.auth.datascope.DataScopeAutoConfiguration`         | `io.ddd4j.auth.datascope` | 🟡 配置自洽但**包名应为 `io.ddd4j.data.datascope`**（从 auth 迁来未改名） |

### 2.3 Spring/Web 耦合现状（逐项核实）

| 子模块           | Spring imports | 耦合点                                                                                                                                                    | 应迁移到                    |
|---------------|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------|
| **mybatis**   | 10 处           | `BaseRepositoryImpl`：`@Autowired`/`@Transactional`/`org.springframework.lang.NonNull`；`MybatisExceptionHandler`：`@ControllerAdvice`（依赖 ddd4j-web-core） | data-spring + boot-data |
| **crypto**    | 24 处           | `DefaultCryptoAutoConfiguration`：`@Configuration`+`@Bean`；`DecryptRequestBodyAdvice`/`EncryptResponseBodyAdvice`：依赖 `spring-webmvc`                    | boot-data               |
| **external**  | —              | `ExternalAutoConfiguration`：`@Configuration`；依赖 `ip2region-spring-boot-starter`+`redistpl-plus-spring-boot-starter`                                    | boot-data               |
| **logs**      | —              | `ApiLogAspectConfiguration`：`@Configuration`；`ApiOperationLogAspect`：`@Aspect`+`@Component`（缺 aspectj 依赖声明）                                            | boot-data               |
| **datascope** | —              | `DataScopeAutoConfiguration`：`@Configuration`；依赖 `spring-biz`                                                                                          | boot-data               |

### 2.4 SPI 对齐现状（逐项核实）

| SPI                            | 现状    | 证据                                                                   |
|--------------------------------|-------|----------------------------------------------------------------------|
| `Repository<M,Q,P>`（新版 3 泛型）   | ❌ 未实现 | `BaseRepositoryImpl implements BaseRepository<M,Q>`（仅旧版 2 泛型，第 47 行） |
| `BaseRepository<M,Q>`（旧版 2 泛型） | ✅ 已实现 | 同上                                                                   |
| `TypeHandlerRegistry`          | ❌ 零实现 | 17 个 TypeHandler 全是 MyBatis 原生接口，无 `*TypeHandlerRegistry*` 文件        |

### 2.5 POM 残留问题（逐项核实）

| 子模块           | 问题                                    | 证据                                         |
|---------------|---------------------------------------|--------------------------------------------|
| **external**  | `spring-web` 重复 3 次                   | `grep -c "spring-web"` = 3                 |
| **logs**      | 缺 aspectj 依赖声明                        | pom 无 `aspectjweaver`，但源码用 `@Aspect`       |
| **external**  | 缺 `<description>`                     | pom 无 description 标签                       |
| **datascope** | 包名 `io.ddd4j.auth` 应为 `io.ddd4j.data` | 4 个 Java 文件都在 `io.ddd4j.auth.datascope` 包下 |
| **聚合 pom**    | description 未提及 datascope             | 仍写 "mybatis + crypto/external/logs 扩展"     |

### 2.6 ddd4j-boot-data 现状（迁移目标，已核实）

```
ddd4j-boot/ddd4j-boot-data/
├── src/main/java/io/ddd4j/data/config/
│   └── BaseDataConfig.java              ← 已有：MyBatis 拦截器 auto-config（正确）
└── src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
        └── io.ddd4j.data.config.BaseDataConfig   ← 仅注册了 1 个
```

**pom 依赖**：`ddd4j-data` + `ddd4j-data-mybatis` + `spring-boot-autoconfigure` + `mybatis-plus`（结构正确，等待迁入更多
auto-config）。

---

## 三、优化方案（4 阶段）

### 阶段 1：通用层清除 Spring Boot auto-config（P0，1-2h）

> **目标**：把 `ddd4j-data` 通用层里的 Spring Boot auto-config 代码全部迁移到 `ddd4j-boot/ddd4j-boot-data`，让通用层回归"
> 纯实现 + 可选 Spring 桥接"的定位。

#### 1.1 迁移 crypto 的 auto-config + WebMVC Advice

**从** `ddd4j-data/ddd4j-data-crypto` → **到** `ddd4j-boot/ddd4j-boot-data`：

| 迁移项                                                    | 源路径                                              | 目标路径                                                                |
|--------------------------------------------------------|--------------------------------------------------|---------------------------------------------------------------------|
| `DefaultCryptoAutoConfiguration.java`                  | `.../crypto/DefaultCryptoAutoConfiguration.java` | `ddd4j-boot-data/.../data/config/Ddd4jCryptoAutoConfiguration.java` |
| `CryptoProperties.java`（若有 `@ConfigurationProperties`） | `.../crypto/CryptoProperties.java`               | `ddd4j-boot-data/.../data/config/CryptoProperties.java`             |
| `DecryptRequestBodyAdvice.java`                        | `.../crypto/advice/`                             | `ddd4j-boot-data/.../data/advice/`                                  |
| `EncryptResponseBodyAdvice.java`                       | `.../crypto/advice/`                             | `ddd4j-boot-data/.../data/advice/`                                  |

**crypto 保留**：纯 Java 部分（`CryptoStrategy`/`CryptoProvider`/`DefaultCryptoProvider`/`FlksecCryptoStrategy`/
`NoOpCryptoStrategy`/枚举/VO/注解）。

**crypto pom 改动**：移除 `spring-webmvc` 依赖（WebMVC Advice 已迁走）；移除 metadata 文件（auto-config 已迁走）。

#### 1.2 迁移 external 的 auto-config

**从** `ddd4j-data/ddd4j-data-external` → **到** `ddd4j-boot/ddd4j-boot-data`：

| 迁移项                              | 源路径                                           | 目标路径                                                                  |
|----------------------------------|-----------------------------------------------|-----------------------------------------------------------------------|
| `ExternalAutoConfiguration.java` | `.../external/ExternalAutoConfiguration.java` | `ddd4j-boot-data/.../data/config/Ddd4jExternalAutoConfiguration.java` |
| `AutoConfiguration.imports`      | `.../spring/...imports`                       | 合并到 boot-data 的 imports                                               |
| `spring/` 目录                     | 整个删除                                          | —                                                                     |

**external 保留**：纯 Java 部分（Template/Enum/Sequence/VO）。

**external pom 改动**：删除重复的 2 个 `spring-web`（保留 1 个）；补充 `<description>`。

#### 1.3 迁移 logs 的 auto-config

**从** `ddd4j-data/ddd4j-data-logs` → **到** `ddd4j-boot/ddd4j-boot-data`：

| 迁移项                               | 源路径                                       | 目标路径                                                                      |
|-----------------------------------|-------------------------------------------|---------------------------------------------------------------------------|
| `ApiLogAspectConfiguration.java`  | `.../logs/ApiLogAspectConfiguration.java` | `ddd4j-boot-data/.../data/config/Ddd4jApiLogAspectAutoConfiguration.java` |
| `AutoConfiguration.imports`（修正包名） | `.../spring/...imports`                   | 合并到 boot-data 的 imports                                                   |

**logs 保留**：纯 AspectJ 部分（`ApiOperationLogAspect`/`ApiOperationLogProvider`/`DefaultApiOperationLogProvider`）。

**logs pom 改动**：补充 `aspectjweaver` 依赖；删除 metadata 和 imports（auto-config 已迁走）。

#### 1.4 迁移 datascope 的 auto-config + 修正包名

**从** `ddd4j-data/ddd4j-data-datascope` → **到** `ddd4j-boot/ddd4j-boot-data`：

| 迁移项                               | 源路径                                                  | 目标路径                                                                   |
|-----------------------------------|------------------------------------------------------|------------------------------------------------------------------------|
| `DataScopeAutoConfiguration.java` | `.../auth/datascope/DataScopeAutoConfiguration.java` | `ddd4j-boot-data/.../data/config/Ddd4jDataScopeAutoConfiguration.java` |

**datascope 保留**：纯 Java 部分（`DataScopeProvider`/`RequiresDataPermissionsValidator`/`RequiresDataPermissions` 注解）。

**datascope 包名修正**：`io.ddd4j.auth.datascope` → `io.ddd4j.data.datascope`（4 个文件 + metadata + imports 同步改）。

**datascope pom 改动**：移除 `spring-biz` 依赖中的 auto-config 部分（保留纯 Java 依赖）；删除 metadata 和 imports。

#### 1.5 清除 mybatis 的错误配置

**清空** `ddd4j-data/ddd4j-data-mybatis/src/main/resources/META-INF/spring-autoconfigure-metadata.properties`（内容错误指向
crypto 类）。

mybatis 模块本就不该有 auto-config——拦截器装配已在 `ddd4j-boot/ddd4j-boot-data` 的 `BaseDataConfig` 中。

#### 1.6 ddd4j-boot-data 统一注册

`ddd4j-boot/ddd4j-boot-data/.../AutoConfiguration.imports` 更新为：

```
io.ddd4j.data.config.BaseDataConfig
io.ddd4j.data.config.Ddd4jCryptoAutoConfiguration
io.ddd4j.data.config.Ddd4jExternalAutoConfiguration
io.ddd4j.data.config.Ddd4jApiLogAspectAutoConfiguration
io.ddd4j.data.config.Ddd4jDataScopeAutoConfiguration
```

`ddd4j-boot/ddd4j-boot-data/pom.xml` 补充依赖：

```xml
<!-- crypto 加解密 -->
<dependency><groupId>cn.hutool</groupId><artifactId>hutool-crypto</artifactId></dependency>
<dependency><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId></dependency>
<!-- logs aspectj -->
<dependency><groupId>org.aspectj</groupId><artifactId>aspectjweaver</artifactId></dependency>
<!-- external（按需，可选）-->
<!-- datascope spring-biz -->
<dependency><groupId>com.github.hiwepy</groupId><artifactId>spring-biz</artifactId></dependency>
```

#### 1.7 更新聚合 pom description

`ddd4j-data/pom.xml`：

```xml
<description>ddd4j 数据抽象聚合：mybatis(实现) + crypto/external/logs/datascope 扩展。数据 SPI（Repository/TypeHandlerRegistry）已收敛到 ddd4j-core。Spring Boot auto-config 迁移到 ddd4j-boot-data。</description>
```

**阶段 1 验收**：

```bash
# 通用层无任何 Spring Boot auto-config
! find ddd4j-data -path "*/spring/*.imports"
! find ddd4j-data -name "*AutoConfiguration.java"
# 通用层无 spring/ 资源目录
! find ddd4j-data -type d -name "spring"
# mybatis metadata 已清除
! grep -q "." ddd4j-data/ddd4j-data-mybatis/src/main/resources/META-INF/spring-autoconfigure-metadata.properties
# boot-data 统一注册了全部 auto-config
test $(grep -c "AutoConfiguration" ddd4j-boot/ddd4j-boot-data/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) -eq 5
```

---

### 阶段 2：新增 ddd4j-data-spring 桥接层（P1，4-8h）

> **目标**：参照 `ddd4j-mq-spring` 范式，把 mybatis 实现层的 Spring 桥接代码（非 auto-config）抽到独立模块。

#### 2.1 新增 ddd4j-data-spring 模块

```
ddd4j-data/
├── ddd4j-data-spring/                      ← 新增（参照 ddd4j-mq-spring）
│   ├── pom.xml
│   └── src/main/java/io/ddd4j/data/spring/
│       ├── RepositoryBeanPostProcessor.java    ← 扫描 BaseRepositoryImpl，注入 mapper
│       └── RepositoryRegistrar.java            ← BaseRepository 静态注册表初始化
```

**ddd4j-data-spring/pom.xml**：

```xml
<artifactId>ddd4j-data-spring</artifactId>
<description>ddd4j Data - Spring 桥接：Repository Bean 注册、静态注册表初始化。承担 ddd4j-data-mybatis 与 Spring 容器的桥接职责（非 auto-config）。</description>
<dependencies>
    <dependency>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-data-mybatis</artifactId>
        <version>${revision}</version>
    </dependency>
    <dependency>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-adapter-spring</artifactId>
        <version>${revision}</version>
    </dependency>
</dependencies>
```

#### 2.2 mybatis 模块瘦身

**改造 `BaseRepositoryImpl`**（最小侵入）：

```java
// 改造前：@Autowired 直接注入
public abstract class BaseRepositoryImpl<MP, M, P, Q> implements BaseRepository<M, Q> {
    @Autowired private MP mapper;
    @Autowired BaseDataProperties baseDataProperties;
}

// 改造后：纯 Java setter 注入，Spring 注解移到 data-spring 的 BeanPostProcessor
public abstract class BaseRepositoryImpl<MP, M, P, Q>
        implements BaseRepository<M, Q>, Repository<M, Q, Serializable> {
    private MP mapper;
    private BaseDataProperties baseDataProperties;

    public void setMapper(MP mapper) { this.mapper = mapper; }
    public void setBaseDataProperties(BaseDataProperties props) { this.baseDataProperties = props; }
}
```

**迁移**：

- `MybatisExceptionHandler`（`@ControllerAdvice`，依赖 web-core）→ `ddd4j-boot/ddd4j-boot-data`（web 异常属于 boot 层）
- mybatis pom 移除 `ddd4j-web-core` 依赖
- `@Transactional` 保留在方法上（Spring AOP 代理，无需改代码）
- 移除 swagger-annotations / jakarta.validation 残留依赖（仅为 3 个迁移的 DTO/Param 服务）

**阶段 2 验收**：

```bash
# data-spring 模块存在
test -d ddd4j-data/ddd4j-data-spring
# mybatis 无 @Autowired
! grep "@Autowired" ddd4j-data/ddd4j-data-mybatis/src/main/java/io/ddd4j/data/mybatis/repository/impl/BaseRepositoryImpl.java
# mybatis 不依赖 web-core
! grep "ddd4j-web-core" ddd4j-data/ddd4j-data-mybatis/pom.xml
```

---

### 阶段 3：SPI 对齐（P1，2-4h）

#### 3.1 BaseRepositoryImpl 实现新版 Repository

```java
// 同时实现旧版 BaseRepository + 新版 Repository（向后兼容）
public abstract class BaseRepositoryImpl<MP extends BaseMapper<P>, M extends Model, P, Q extends Query>
        implements BaseRepository<M, Q>, Repository<M, Q, Serializable>, Serializable {

    // === Repository<M,Q,P> 新接口（委托现有方法） ===
    @Override public Optional<M> findById(Serializable id) { return Optional.ofNullable(this.get(id)); }
    @Override public Optional<M> findOne(Q query) { return Optional.ofNullable(this.one(query)); }
    @Override public List<M> findList(Q query) { return this.list(query); }
    @Override public long count(Q query) { return this.count(query); }
    @Override public M save(M entity) { this.save((M) entity); return entity; }
    // ... 其余委托方法
}
```

#### 3.2 新增 MybatisTypeHandlerRegistry

**新建** `ddd4j-data/ddd4j-data-mybatis/.../typehandler/MybatisTypeHandlerRegistry.java`：

```java
public class MybatisTypeHandlerRegistry implements TypeHandlerRegistry {
    private final Map<Class<?>, TypeHandler<?, ?>> handlers = new ConcurrentHashMap<>();
    public MybatisTypeHandlerRegistry() {
        register(List.class, new ListStringTypeHandler());
        register(Set.class, new SetStringTypeHandler());
        // ... 17 个 TypeHandler
    }
    // 实现 register / lookup
}
```

**注册到 boot 层**：`ddd4j-boot/ddd4j-boot-data` 的 `BaseDataConfig` 追加：

```java
@Bean
@ConditionalOnMissingBean(TypeHandlerRegistry.class)
public TypeHandlerRegistry mybatisTypeHandlerRegistry() {
    return new MybatisTypeHandlerRegistry();
}
```

**阶段 3 验收**：

```bash
grep "Repository<M, Q, Serializable>" ddd4j-data/ddd4j-data-mybatis/src/main/java/io/ddd4j/data/mybatis/repository/impl/BaseRepositoryImpl.java
find ddd4j-data -name "*TypeHandlerRegistry*" -path "*/main/*"
```

---

### 阶段 4：测试补齐（P2，4-8h）

| 模块                   | 新增测试                                  | 优先级 |
|----------------------|---------------------------------------|-----|
| ddd4j-data-mybatis   | `BaseRepositoryImplTest`（CRUD，H2 内存库） | P0  |
| ddd4j-data-mybatis   | `TypeHandlerTest`（17 个 TypeHandler）   | P1  |
| ddd4j-data-crypto    | `CryptoStrategyTest`                  | P1  |
| ddd4j-data-datascope | `DataScopeProviderTest`               | P2  |
| ddd4j-boot-data      | `BaseDataConfigTest`（auto-config 验证）  | P1  |

---

## 四、优化后的目标结构

```
ddd4j/ddd4j-data/（通用基础层：纯 Java + Spring 桥接）
├── ddd4j-data-mybatis/        ← 纯 MyBatis 实现（零 Spring/web 耦合）
│   ├── repository/impl/BaseRepositoryImpl.java  ← implements Repository + BaseRepository
│   ├── typehandler/
│   │   ├── MybatisTypeHandlerRegistry.java     ← 新增：实现 core 的 TypeHandlerRegistry
│   │   └── (17 个 TypeHandler)
│   ├── plugin/                ← 纯 MyBatis 拦截器
│   └── annotation/ dto/ param/ enums/
├── ddd4j-data-spring/         ← 新增：Spring 桥接（参照 mq-spring）
│   ├── RepositoryBeanPostProcessor.java
│   └── RepositoryRegistrar.java
├── ddd4j-data-crypto/         ← 纯 Java 加解密策略（auto-config 已迁走）
│   └── strategy/ provider/ domain/
├── ddd4j-data-external/       ← 纯 Java 外部服务（auto-config 已迁走）
├── ddd4j-data-logs/           ← 纯 AspectJ（auto-config 已迁走）
└── ddd4j-data-datascope/      ← 纯 Java 数据权限（包名改为 io.ddd4j.data.datascope）

ddd4j-boot/ddd4j-boot-data/（Spring Boot 整合层：auto-config）
├── src/main/java/io/ddd4j/data/
│   ├── config/
│   │   ├── BaseDataConfig.java              ← 已有：MyBatis 拦截器
│   │   ├── Ddd4jCryptoAutoConfiguration.java ← 迁入
│   │   ├── Ddd4jExternalAutoConfiguration.java ← 迁入
│   │   ├── Ddd4jApiLogAspectAutoConfiguration.java ← 迁入
│   │   └── Ddd4jDataScopeAutoConfiguration.java ← 迁入
│   ├── advice/                              ← 迁入：crypto WebMVC Advice
│   │   ├── DecryptRequestBodyAdvice.java
│   │   └── EncryptResponseBodyAdvice.java
│   └── web/                                 ← 迁入：MybatisExceptionHandler
│       └── MybatisExceptionHandler.java
└── src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← 统一注册 5 个
```

---

## 五、与 mq 模块分层对照确认

| 层次                      | ddd4j-mq（范本）                                | ddd4j-data（优化后）                                             |
|-------------------------|---------------------------------------------|-------------------------------------------------------------|
| 纯 Java SPI              | `ddd4j-mq-core`                             | ✅ 复用 `ddd4j-core`（Repository/TypeHandlerRegistry）           |
| Spring 桥接               | `ddd4j-mq-spring`                           | ✅ `ddd4j-data-spring`（新增）                                   |
| 纯实现层                    | `ddd4j-mq-kafka`（零 Spring）                  | ✅ `ddd4j-data-mybatis`（零 Spring/web）                        |
| Spring Boot auto-config | `ddd4j-boot-mq`（`Ddd4jMQAutoConfiguration`） | ✅ `ddd4j-boot-data`（`BaseDataConfig` + 迁入的 4 个 auto-config） |
| auto-config 注册          | `ddd4j-boot-mq/.imports`                    | ✅ `ddd4j-boot-data/.imports` 统一注册 5 个                       |

---

## 六、实施顺序与工时

```
阶段 1（auto-config 迁移）  → 阶段 2（data-spring 桥接）  → 阶段 3（SPI 对齐）  → 阶段 4（测试）
  1-2h                        4-8h                          2-4h                   4-8h
  通用层回归纯净              Spring 代码隔离                新旧 SPI 共存           质量达标
```

**总工时**：11-22 小时（1-3 个工作日）

---

## 七、风险评估

| 风险                                    | 等级 | 缓解措施                                            |
|---------------------------------------|----|-------------------------------------------------|
| auto-config 迁移导致现有 Spring Boot 项目装配失效 | 高  | 迁移后在 `ddd4j-boot-data` 的 `.imports` 统一注册，业务项目无感 |
| BaseRepositoryImpl 改 setter 注入影响现有子类  | 高  | 保留旧构造器逻辑，setter 仅作 Spring 桥接补充                  |
| crypto WebMVC Advice 迁移影响加解密          | 中  | 迁移到 boot-data 后通过 auto-config 自动注册              |
| datascope 包名改为 io.ddd4j.data 影响引用     | 中  | 同步改 imports/metadata，全项目 grep 替换                |
| external 的 hiwepy starter 强耦合         | 低  | 暂不处理，标注为"Spring 专属扩展"                           |
