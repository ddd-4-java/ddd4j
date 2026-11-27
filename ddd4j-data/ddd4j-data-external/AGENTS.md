# ddd4j-data-external

地理、地区、天气与序列号服务适配。

## 实现与调用

从 [GeoBaiduTemplate](src/main/java/io/ddd4j/data/external/geo/GeoBaiduTemplate.java)、[ExternalProperties](src/main/java/io/ddd4j/data/external/ExternalProperties.java)、[SequenceProperties](src/main/java/io/ddd4j/data/external/SequenceProperties.java) 进入。

修改 `GeoBaiduTemplate` 时，核对公开入口 `main` 的调用方。

## 回归

本工件没有独立测试类。修改公开接口后，另选实际消费模块执行行为回归。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-external -am verify
```
