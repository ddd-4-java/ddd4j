# ddd4j-cache

> 基于 ddd4j 核心 Cache SPI 的多后端缓存实现层，单一 jar，按需引入客户端，零 Spring 依赖。

## 一、设计理念

### 为什么需要 ddd4j-cache？

在真实的微服务项目中，缓存需求是分层的：

- **开发/测试阶段**：用本地缓存（Caffeine）即可，零基础设施
- **单实例生产**：用 Redis 单节点（Jedis / Lettuce）
- **多实例高并发**：用 Redis 集群 + 分布式锁（Redisson）
- **极致性能**：用多级缓存 LOCAL + REMOTE（JetCache）
- **遗留系统**：可能还在用 Memcached

如果每种缓存都引入一套独立的 API，业务代码会被缓存客户端深度绑定，切换成本极高。

**ddd4j-cache 的价值**：定义一套纯 Java 的 `Cache` SPI（在 `ddd4j-core/cache` 中），所有后端实现统一适配到这套 SPI。业务代码面向 `Cache<K,V>` 接口编程，切换缓存后端只需换一行构造代码。

### 架构分层

```
┌──────────────────────────────────────────────────────────────┐
│                     业务代码                                  │
│            面向 io.ddd4j.core.cache.Cache 编程                │
└──────────────────────────┬───────────────────────────────────┘
                           │ 依赖
┌──────────────────────────▼───────────────────────────────────┐
│              ddd4j-core/cache （纯 Java SPI）                  │
│                                                              │
│  Cache<K,V>    CacheStats    CacheManager    CacheConfig     │
│  CacheType                                                    │
│  （零框架依赖，Javalin / Quarkus / Spring 均可用）              │
└──────────────────────────┬───────────────────────────────────┘
                           │ 依赖
┌──────────────────────────▼───────────────────────────────────┐
│                   ddd4j-cache （单一 jar）                     │
│                                                              │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐        │
│  │ Caffeine│ │ Guava    │ │ Hutool   │ │  JetCache │        │
│  │ (本地)  │ │ (本地)   │ │ (本地)   │ │  (多级)  │        │
│  └─────────┘ └──────────┘ └──────────┘ └───────────┘        │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐        │
│  │ Jedis   │ │ Lettuce  │ │ Redisson │ │ Memcached │        │
│  │ (Redis) │ │ (Redis)  │ │ (Redis)  │ │           │        │
│  └─────────┘ └──────────┘ └──────────┘ └───────────┘        │
│                                                              │
│  外部客户端全部 optional，按需引入                              │
└──────────────────────────────────────────────────────────────┘
```

### 核心原则

| 原则 | 说明 |
|------|------|
| **SPI 与实现分离** | `Cache`/`CacheManager` 接口在 `ddd4j-core/cache`（纯 Java），实现在 `ddd4j-cache` |
| **零 Spring 依赖** | 整个 `ddd4j-cache` 模块不依赖 Spring / Spring Boot，可被任何框架使用 |
| **单一 jar** | 所有后端实现打包在一个 jar 中，按包名分层，外部客户端 `optional` |
| **按需引入** | 消费方只引入用到的客户端依赖（如 `redisson`），未引入的不会传递 |
| **统一 API** | 业务代码面向 `Cache<K,V>` 接口编程，切换后端零业务代码改动 |

---

## 二、快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-cache</artifactId>
    <version>${ddd4j.version}</version>
</dependency>
```

Caffeine 本地缓存开箱即用（compile scope），无需额外依赖。

### 2. 本地缓存（Caffeine，最简用法）

```java
import io.ddd4j.cache.CacheKit;

// 构建缓存（业务标识 "user"，写后 300 秒过期）
CacheKit.build("user", 300);

// 写入
CacheKit.put("user", "123", userObject);

// 读取
User user = CacheKit.get("user", "123");

// 删除
CacheKit.invalidate("user", "123");

// 清空
CacheKit.invalidateAll("user");
```

### 3. 本地缓存（详细配置）

```java
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.CacheConfig;

CacheKit.build("user", config -> config
    .expireAfterWriteSeconds(300)      // 写后 5 分钟过期
    .expireAfterAccessSeconds(600)     // 访问后 10 分钟过期
    .maximumSize(10_000)               // 最大 1 万条
    .recordStats(true)                 // 开启统计
);

CacheKit.build("user", config -> config
    .expireAfterWriteSeconds(300), CacheKit.LocalCacheType.GUAVA  // 切换 Guava 后端
);
```

### 4. 自动加载缓存

```java
// 缓存未命中时自动从数据库加载
CacheKit.buildWithLoader("config",
    key -> loadFromDatabase(key),           // 加载器
    config -> config
        .expireAfterWriteSeconds(60)        // 写后 60 秒过期
        .refreshAfterWriteSeconds(30)       // 写后 30 秒异步刷新
);

// 获取（未命中自动加载，无需手动 put）
String value = CacheKit.get("config", "app.name");

// 手动刷新
CacheKit.refresh("config", "app.name");
```

### 5. 远程缓存（Redis / Redisson / Memcached / JetCache）

远程缓存通过 `CacheKit.register()` 注册后，操作方式与本地缓存完全一致：

```java
import io.ddd4j.cache.CacheKit;
import io.ddd4j.cache.redisson.RedissonCache;
import io.ddd4j.core.cache.CacheConfig;

// 创建 Redisson 缓存实例并注册
RedissonCache<User> cache = new RedissonCache<>(redissonClient,
    CacheConfig.builder("user").expireAfterWriteSeconds(300).build(),
    User.class);
CacheKit.register("user", cache);

// 注册后操作方式与本地缓存完全一致
CacheKit.put("user", "123", user);
User u = CacheKit.get("user", "123");
CacheKit.invalidate("user", "123");
```

> 各远程后端的完整示例见[第四章](#四各后端实现详解)。

---

## 三、缓存 SPI 契约

SPI 接口定义在 `ddd4j-core/cache`（`io.ddd4j.core.cache` 包），纯 Java，零框架依赖。

### Cache&lt;K,V&gt;

```java
public interface Cache<K, V> {
    V getIfPresent(K key);                              // 获取，不存在返回 null
    V get(K key);                                        // 获取（自动加载缓存专用）
    V get(K key, Function<K, V> mappingFunction);        // 获取，不存在则加载
    void put(K key, V value);                            // 写入
    void putAll(Map<K, V> map);                          // 批量写入
    void invalidate(K key);                              // 删除
    void refresh(K key);                                 // 刷新（自动加载缓存专用）
    void invalidateAll(Iterable<K> keys);                // 批量删除
    void invalidateAll();                                // 清空
    long estimatedSize();                                // 估算大小
    CacheStats stats();                                  // 统计信息
}
```

### CacheConfig（Builder 模式）

```java
CacheConfig config = CacheConfig.builder("user")        // 缓存名称（业务标识）
    .maximumSize(10_000)                                 // 最大容量
    .expireAfterWriteSeconds(300)                        // 写后过期（秒）
    .expireAfterAccessSeconds(600)                       // 访问后过期（秒）
    .refreshAfterWriteSeconds(60)                        // 写后刷新（秒）
    .initialCapacity(100)                                // 初始容量
    .recordStats(true)                                   // 是否记录统计
    .cacheType(CacheType.BOTH)                           // LOCAL / REMOTE / BOTH
    .localLimit(1000)                                    // 多级缓存时本地缓存限制
    .syncLocal(true)                                     // 多级缓存时是否同步本地
    .removalListener(key -> log.info("移除: {}", key))   // 移除监听器
    .build();
```

### CacheManager（SPI 接口）

```java
public interface CacheManager {
    <K, V> Cache<K, V> getOrCreateCache(String name, CacheConfig config);     // 创建或获取
    <K, V> Cache<K, V> getCache(String name);                                  // 获取已注册的
    Set<String> getCacheNames();                                               // 获取所有名称
    <K, V> Cache<K, V> getOrCreateLoadingCache(String name, CacheConfig config, Function<K, V> loader);  // 自动加载
}
```

---

## 四、各后端实现详解

### 能力矩阵

| 实现 | 类型 | 自动加载 | 统计 | 分布式锁 | 过期策略 | 需引入的依赖 |
|------|------|---------|------|---------|---------|------------|
| **CaffeineCache** | 本地 | ✅ refresh | ✅ 完整 | — | 写后 / 访问后 / 刷新 | 默认包含 |
| **GuavaCache** | 本地 | — | — | — | 写后 / 访问后 | `com.google.guava:guava` |
| **HutoolCache** | 本地 | — | — | — | 仅写后 | `cn.hutool:hutool-cache` |
| **JedisCache** | 远程 | — | — | — | 写后 TTL | `redis.clients:jedis` |
| **LettuceCache** | 远程 | — | — | — | 写后 TTL | `io.lettuce:lettuce-core` |
| **RedissonCache** | 远程 | — | — | ✅ | 写后 TTL | `org.redisson:redisson` |
| **MemcachedCache** | 远程 | — | — | — | 写后 TTL | `com.googlecode.xmemcached:xmemcached` |
| **JetCacheAdapter** | 多级 | ✅ computeIfAbsent | — | ✅ | 可配置 | `com.alicp.jetcache:jetcache-core` |

### 统一调用原则

**所有缓存——无论本地还是远程——都通过 `CacheKit` 调用。** 区别只在注册方式：

| 场景 | 注册方式 | 说明 |
|------|---------|------|
| 本地缓存（Caffeine/Guava/Hutool） | `CacheKit.build(biz, ...)` | 门面内部根据配置自动创建 |
| 远程缓存（Jedis/Lettuce/Redisson/Memcached） | `CacheKit.register(biz, cache)` | 调用方 new 出实例后注册 |
| JetCache 多级缓存 | `CacheKit.register(biz, cache)` | 通过 JetCacheCacheManager 创建后注册 |
| 自动加载缓存 | `CacheKit.buildWithLoader(biz, ...)` 或 `registerLoading` | 未命中自动加载 |

注册后，所有缓存的读写操作完全一致：`CacheKit.get(biz, key)` / `CacheKit.put(biz, key, value)` / `CacheKit.invalidate(biz, key)`。

### 1. CaffeineCache（本地，默认）

最高性能本地缓存，功能最完整。通过 `build()` 自建：

```java
import io.ddd4j.cache.CacheKit;

// 普通缓存
CacheKit.build("user", config -> config
    .maximumSize(10_000)
    .expireAfterWriteSeconds(300)
    .refreshAfterWriteSeconds(60)
    .recordStats(true)
);

// 自动加载缓存（未命中自动调用 loader）
CacheKit.buildWithLoader("user", key -> loadUser(key),
    config -> config.expireAfterWriteSeconds(300).refreshAfterWriteSeconds(60)
);

// 统一操作
CacheKit.put("user", "123", user);
User u = CacheKit.get("user", "123");
```

### 2. JedisCache（Redis，Jedis 7+）

```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
</dependency>
```

```java
import io.ddd4j.cache.CacheKit;
import io.ddd4j.cache.jedis.JedisCache;
import io.ddd4j.core.cache.CacheConfig;
import redis.clients.jedis.RedisClient;

// 1. 创建客户端实例
RedisClient client = RedisClient.create("localhost", 6379);
CacheConfig config = CacheConfig.builder("user").expireAfterWriteSeconds(300).build();

// 2. 注册到 CacheKit
CacheKit.register("user", new JedisCache<>(client, config, User.class));

// 3. 统一操作
CacheKit.put("user", "123", user);
User u = CacheKit.get("user", "123");
```

### 3. LettuceCache（Redis，Lettuce）

```xml
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>
```

```java
import io.ddd4j.cache.CacheKit;
import io.ddd4j.cache.lettuce.LettuceCache;
import io.ddd4j.core.cache.CacheConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

RedisClient client = RedisClient.create("redis://localhost:6379");
StatefulRedisConnection<String, String> conn = client.connect();
CacheConfig config = CacheConfig.builder("user").expireAfterWriteSeconds(300).build();

// 注册后统一操作
CacheKit.register("user", new LettuceCache<>(conn.sync(), config, User.class));
CacheKit.put("user", "123", user);
```

### 4. RedissonCache（Redis + 分布式锁）

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
</dependency>
```

```java
import io.ddd4j.cache.CacheKit;
import io.ddd4j.cache.redisson.RedissonCache;
import io.ddd4j.core.cache.CacheConfig;
import org.redisson.api.RedissonClient;

RedissonClient client = Redisson.create();
CacheConfig config = CacheConfig.builder("user").expireAfterWriteSeconds(300).build();

// 注册到 CacheKit
RedissonCache<User> redissonCache = new RedissonCache<>(client, config, User.class);
CacheKit.register("user", redissonCache);

// 统一操作
CacheKit.put("user", "123", user);
User u = CacheKit.get("user", "123");

// ★ 分布式锁（通过 getCache() 获取底层实例调用特有 API）
RedissonCache<User> raw = (RedissonCache<User>) CacheKit.getCache("user");
boolean locked = raw.tryLock("resource:1", 5, 30);  // 等待 5 秒，持有 30 秒
```

### 5. MemcachedCache（XMemcached）

```xml
<dependency>
    <groupId>com.googlecode.xmemcached</groupId>
    <artifactId>xmemcached</artifactId>
</dependency>
```

```java
import io.ddd4j.cache.CacheKit;
import io.ddd4j.cache.memcached.MemcachedCache;
import io.ddd4j.core.cache.CacheConfig;
import net.rubyeye.xmemcached.XMemcachedClient;

CacheConfig config = CacheConfig.builder("user").expireAfterWriteSeconds(300).build();

CacheKit.register("user", new MemcachedCache<>(new XMemcachedClient("localhost", 11211), config, User.class));
CacheKit.put("user", "123", user);
```

### 6. JetCache 多级缓存

JetCache 支持 LOCAL + REMOTE 两级联动。通过 `JetCacheCacheManager` 创建后注册到 CacheKit：

```xml
<dependency>
    <groupId>com.alicp.jetcache</groupId>
    <artifactId>jetcache-core</artifactId>
</dependency>
<!-- + JetCache 的 Redis 后端（按需选一个） -->
<dependency>
    <groupId>com.alicp.jetcache</groupId>
    <artifactId>jetcache-redis-lettuce</artifactId>
</dependency>
```

```java
import io.ddd4j.cache.CacheKit;
import io.ddd4j.cache.jetcache.adapter.JetCacheCacheManager;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheType;

// 1. 创建 JetCache 管理器（jetCacheManager 由 JetCache 初始化提供）
JetCacheCacheManager manager = new JetCacheCacheManager(jetCacheManager);

// 2. 创建多级缓存（LOCAL + REMOTE）
CacheConfig config = CacheConfig.builder("user")
    .expireAfterWriteSeconds(300)
    .cacheType(CacheType.BOTH)
    .localLimit(1000)
    .syncLocal(true)
    .build();
Cache<String, User> multiCache = manager.getOrCreateCache("user", config);

// 3. 注册到 CacheKit，统一操作
CacheKit.register("user", multiCache);
CacheKit.put("user", "123", user);
User u = CacheKit.get("user", "123");
```

---

## 五、包结构

```
io.ddd4j.cache/
├── CacheKit                              ← 统一缓存门面（所有后端的唯一入口）
│
├── local/                                ← 本地缓存实现
│   ├── CaffeineCache<K,V>                ← Caffeine（默认，compile）
│   ├── GuavaCache<K,V>                   ← Guava（optional）
│   └── HutoolCache<K,V>                  ← Hutool（optional）
│
├── jedis/
│   └── JedisCache<V>                     ← Jedis 5+/7+ UnifiedJedis（optional）
│
├── lettuce/
│   └── LettuceCache<V>                   ← Lettuce RedisCommands（optional）
│
├── redisson/
│   └── RedissonCache<V>                  ← Redisson + 分布式锁（optional）
│
├── memcached/
│   └── MemcachedCache<K,V>               ← XMemcached（optional）
│
└── jetcache/                             ← JetCache 适配层（optional）
    ├── adapter/
    │   ├── JetCacheAdapter<K,V>          ← JetCache Cache → ddd4j Cache
    │   └── JetCacheCacheManager          ← ddd4j CacheManager 实现
    └── config/
        └── CacheConfigConverter          ← CacheConfig → JetCache QuickConfig
```

---

## 六、依赖速查

| 场景 | 需引入的依赖 |
|------|------------|
| 本地缓存（Caffeine） | 仅 `ddd4j-cache`（默认包含） |
| 本地缓存（Guava） | + `com.google.guava:guava` |
| 本地缓存（Hutool） | + `cn.hutool:hutool-cache` |
| Redis（Jedis） | + `redis.clients:jedis` |
| Redis（Lettuce） | + `io.lettuce:lettuce-core` |
| Redis（Redisson + 锁） | + `org.redisson:redisson` |
| Memcached | + `com.googlecode.xmemcached:xmemcached` |
| 多级缓存（JetCache） | + `com.alicp.jetcache:jetcache-core` + 后端 starter |

> **所有外部客户端依赖在 ddd4j-cache 中标记为 `optional`**，不会传递给消费方。消费方按需在自身 pom 中引入。

---

## 七、设计决策

### 为什么 Cache SPI 在 ddd4j-core 而不是 ddd4j-cache？

`ddd4j-core` 是所有框架适配层（Spring / Javalin / Quarkus）的共同依赖。将 Cache SPI 放在 core 中，使得：
- Javalin 项目仅依赖 `ddd4j-core` 即可获得缓存接口
- 缓存实现（Caffeine / Redis 等）按需引入 `ddd4j-cache`
- 业务代码不直接依赖 `ddd4j-cache`

### 为什么不用 JetCache 替换 ddd4j 自有 Cache 接口？

`jetcache-core` 强依赖 `fastjson2` + `caffeine`，不适合作为纯 Java 核心契约的依赖。ddd4j 保持自有轻量 Cache SPI，JetCache 作为可选实现层适配。

### 为什么合并为单一 jar 而非多模块？

多模块（cache-core / cache-jedis / cache-lettuce...）虽然职责更清晰，但管理成本高（6 个 pom + 6 个发布物）。单一 jar + `optional` 依赖的方案：
- 消费方只引一个 `ddd4j-cache` 坐标
- 编译期所有实现类可见（IDE 自动补全友好）
- 运行时只有引入的客户端依赖才会被加载
- 发布和维护只管一个 artifact

### 为什么 JetCache 的 Spring Boot AutoConfig 不在本模块？

ddd4j 通用模块不能与 Spring / Spring Boot 绑定。JetCache 的 `@Cached` 注解、`jetcache-autoconfigure` 等 Spring Boot 集成由下游 `ddd4j-boot` 项目提供（如 `ddd4j-boot-cache-jetcache`）。

---

## 八、版本与兼容性

| 维度 | 说明 |
|------|------|
| Java | 17+ |
| Jedis | 5+ / 7+（UnifiedJedis API） |
| Spring | 不依赖（零 Spring 耦合） |
| JetCache | 2.8+ |
| Caffeine | 3.x |
| 协议 | Apache 2.0 |

---

*作者：[PartMe.AI](https://github.com/partme-ai) · ddd4j 2.0.x*
