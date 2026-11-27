# ddd4j-data-crypto

字段加解密处理器与本地、远端策略。

字段处理器会区分 null、非字符串和空值。变更解密时分别测试远端返回明文和策略失败时的原值处理；不要把输入密文误当作远端的解密结果。HTTP 协议字段归具体策略。

实现入口见 [Ddd4jFieldCryptoHandler](src/main/java/io/ddd4j/data/crypto/handler/Ddd4jFieldCryptoHandler.java)、[FlksecCryptoStrategy](src/main/java/io/ddd4j/data/crypto/strategy/FlksecCryptoStrategy.java)。

## 回归

优先运行 [Ddd4jFieldCryptoHandlerContractTest](src/test/java/io/ddd4j/data/crypto/handler/Ddd4jFieldCryptoHandlerContractTest.java)、[FlksecCryptoHttpContractTest](src/test/java/io/ddd4j/data/crypto/strategy/FlksecCryptoHttpContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-crypto -am -Dtest=Ddd4jFieldCryptoHandlerContractTest,FlksecCryptoHttpContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-crypto -am verify
```
