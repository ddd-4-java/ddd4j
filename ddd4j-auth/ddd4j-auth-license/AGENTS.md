# ddd4j-auth-license

TrueLicense 的密钥、安装与授权兼容。

授权资料的兼容性由真实 JKS 密钥验证。改装配或存储时走签发、安装、验证、卸载完整路径；再篡改许可证，确认安装失败且没有留下已安装状态。

实现入口见 [DefaultLicenseAutoConfiguration](src/main/java/io/ddd4j/auth/license/DefaultLicenseAutoConfiguration.java)。

## 回归

优先运行 [TrueLicenseMigrationTest](src/test/java/io/ddd4j/auth/license/TrueLicenseMigrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-license -am -Dtest=TrueLicenseMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-license -am verify
```
