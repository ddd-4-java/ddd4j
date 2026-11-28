# ddd4j-auth-license

新许可证统一签发为 TrueLicense V4。消费端使用现有主题和偏好存储键安装、验签和卸载 V4 或历史 V1 许可证。
升级后可以读取此前已安装的 V1 许可证。密钥库及签名算法继续取自应用配置。
仅支持 V1 的客户端应先升级消费端，再接收 V4 许可证。

授权资料的兼容性由真实 JKS 密钥验证。改装配或存储时走签发、安装、验证、卸载完整路径；再篡改许可证，确认安装失败且没有留下已安装状态。

实现入口见 [DefaultLicenseAutoConfiguration](src/main/java/io/ddd4j/auth/license/DefaultLicenseAutoConfiguration.java)。

## 回归

优先运行 [TrueLicenseMigrationTest](src/test/java/io/ddd4j/auth/license/TrueLicenseMigrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-auth/ddd4j-auth-license -am -Dtest=TrueLicenseMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-auth/ddd4j-auth-license -am verify
```
