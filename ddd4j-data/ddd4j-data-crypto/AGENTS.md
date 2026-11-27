# ddd4j-data-crypto

字段加解密处理器与本地、远端策略。

## 实现与调用

从 [Ddd4jFieldCryptoHandler](src/main/java/io/ddd4j/data/crypto/handler/Ddd4jFieldCryptoHandler.java)、[FlksecCryptoStrategy](src/main/java/io/ddd4j/data/crypto/strategy/FlksecCryptoStrategy.java)、[CryptoProvider](src/main/java/io/ddd4j/data/crypto/provider/CryptoProvider.java) 进入。

修改 `Ddd4jFieldCryptoHandler` 时，核对公开入口 `encrypt`、`decrypt` 的调用方。

修改 `FlksecCryptoStrategy` 时，核对公开入口 `encrypt`、`decrypt`、`hmac` 的调用方。

## 回归

本工件没有独立测试类。修改公开接口后，另选实际消费模块执行行为回归。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-crypto -am verify
```
