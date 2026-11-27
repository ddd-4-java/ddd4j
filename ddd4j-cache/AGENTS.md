# ddd4j-cache

本地与远程缓存的选择、CAS 及过期语义。

## 实现与调用

从 [CacheKit](src/main/java/io/ddd4j/cache/CacheKit.java)、[InMemorySubjectProvider](src/main/java/io/ddd4j/cache/subject/InMemorySubjectProvider.java)、[CaffeineCache](src/main/java/io/ddd4j/cache/local/CaffeineCache.java) 进入。

修改 `CacheKit` 时，核对公开入口 `register`、`registerLoading`、`unregister`、`build` 的调用方。

## 回归

- [LettuceCacheRedisTest](src/test/java/io/ddd4j/cache/lettuce/LettuceCacheRedisTest.java) 中可从 `putAndGetRoundTrip`、`putAppliesTtl` 定位断言。

- [CacheKitLocalCacheTypeTest](src/test/java/io/ddd4j/cache/CacheKitLocalCacheTypeTest.java) 中可从 `values_shouldContainCaffeineGuavaHutool`、`valueOf_shouldResolveByName` 定位断言。

- [CacheKitTest](src/test/java/io/ddd4j/cache/CacheKitTest.java) 中可从 `build_shouldCreateLocalCache`、`build_withBuilder_shouldCreateLocalCache` 定位断言。

```sh
./mvnw -pl ddd4j-cache -am -Dtest=LettuceCacheRedisTest,CacheKitLocalCacheTypeTest,CacheKitTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-cache -am verify
```
