# ddd4j-kit

Bean 转换、JSON 与基础工具。

## 实现与调用

从 [BeanKit](src/main/java/io/ddd4j/kit/lang/BeanKit.java)、[JsonKit](src/main/java/io/ddd4j/kit/lang/JsonKit.java)、[AESKit](src/main/java/io/ddd4j/kit/crypto/AESKit.java) 进入。

修改 `BeanKit` 时，核对公开入口 `copy`、`toMap`、`toMapClean`、`mapToObject` 的调用方。

修改 `JsonKit` 时，核对公开入口 `defaultObjectMapper`、`redisObjectMapper`、`buildObjectMapper`、`serialize` 的调用方。

## 回归

- [BeanKitTest](src/test/java/io/ddd4j/kit/lang/BeanKitTest.java) 中可从 `copy_toTarget_shouldCopyNonNullProperties`、`copy_toTargetClass_shouldReturnNewInstance` 定位断言。

- [CollKitTest](src/test/java/io/ddd4j/kit/lang/CollKitTest.java) 中可从 `isEmpty_collection_shouldReturnTrueForNull`、`isEmpty_collection_shouldReturnTrueForEmptyCollection` 定位断言。

- [IdKitTest](src/test/java/io/ddd4j/kit/lang/IdKitTest.java) 中可从 `nextSnowflakeId_shouldBePositive`、`nextSnowflakeId_withDataCenter_shouldBePositive` 定位断言。

```sh
./mvnw -pl ddd4j-kit -am -Dtest=BeanKitTest,CollKitTest,IdKitTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-kit -am verify
```
