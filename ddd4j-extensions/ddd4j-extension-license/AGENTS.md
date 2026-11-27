# ddd4j-extension-license

许可证生成、机器信息验证及缓存。

验证包含生成、安装、校验和缓存。机器序列号缺失应给出授权不匹配结果；未安装时应返回对应失败状态。改缓存序列化时保留许可证信息用于远程缓存的能力。

实现入口见 [LicenseKit](src/main/java/io/ddd4j/extension/license/LicenseKit.java)、[LicenseMachineInfoProvider](src/main/java/io/ddd4j/extension/license/machine/LicenseMachineInfoProvider.java)。

## 回归

优先运行 [LicenseKitTest](src/test/java/io/ddd4j/extension/license/LicenseKitTest.java)、[CustomLicenseManagerContractTest](src/test/java/io/ddd4j/extension/license/manager/CustomLicenseManagerContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-license -am -Dtest=LicenseKitTest,CustomLicenseManagerContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-license -am verify
```
