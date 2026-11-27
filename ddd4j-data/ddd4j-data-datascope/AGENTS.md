# ddd4j-data-datascope

维护数据范围权限注解、校验器和提供者。调整授权逻辑时覆盖没有提供者、没有权限和已授权三种情况，验证拒绝路径。

## 实现与调用

从 [DataScopeProvider](src/main/java/io/ddd4j/data/datascope/DataScopeProvider.java)、[RequiresDataPermissionsValidator](src/main/java/io/ddd4j/data/datascope/RequiresDataPermissionsValidator.java)、[RequiresDataPermissions](src/main/java/io/ddd4j/data/datascope/annotation/RequiresDataPermissions.java) 进入。

修改 `RequiresDataPermissionsValidator` 时，核对公开入口 `initialize`、`isValid` 的调用方。

## 回归

- [DataScopeProviderTest](src/test/java/io/ddd4j/data/datascope/DataScopeProviderTest.java) 中可从 `defaultProviderShouldAllowOnlyNonNullData`、`customProviderShouldOverrideDefaultProvider` 定位断言。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-datascope -am -Dtest=DataScopeProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-datascope -am verify
```
