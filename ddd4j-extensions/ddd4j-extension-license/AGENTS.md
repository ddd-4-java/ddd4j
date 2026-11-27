# ddd4j-extension-license

许可证生成、机器信息验证及缓存。

## 实现与调用

从 [LicenseMachineInfoProvider](src/main/java/io/ddd4j/extension/license/machine/LicenseMachineInfoProvider.java)、[LicenseKit](src/main/java/io/ddd4j/extension/license/LicenseKit.java)、[DefaultLicenseMachineInfoProvider](src/main/java/io/ddd4j/extension/license/machine/DefaultLicenseMachineInfoProvider.java) 进入。

修改 `LicenseKit` 时，核对公开入口 `from`、`of`、`generate`、`install` 的调用方。

## 回归

- [LicenseKitTest](src/test/java/io/ddd4j/extension/license/LicenseKitTest.java) 中可从 `shouldGenerateInstallVerifyAndCacheLicenseEndToEnd`、`shouldReturnFalseWhenNotInstalled` 定位断言。

- [LicenseInfoTest](src/test/java/io/ddd4j/extension/license/LicenseInfoTest.java) 中可从 `shouldBeSerializableForRemoteCache`、`shouldEvaluateValidity` 定位断言。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-license -am -Dtest=LicenseKitTest,LicenseInfoTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-license -am verify
```
