# ddd4j-data-logs

维护操作日志切面与日志提供者。修改切点时核对成功和异常路径；本线用其现有请求来源获取调用信息。

## 实现与调用

从 [ApiOperationLogAspect](src/main/java/io/ddd4j/data/logs/aspect/ApiOperationLogAspect.java)、[ApiOperationLogProvider](src/main/java/io/ddd4j/data/logs/ApiOperationLogProvider.java)、[DefaultApiOperationLogProvider](src/main/java/io/ddd4j/data/logs/DefaultApiOperationLogProvider.java) 进入。

修改 `ApiOperationLogAspect` 时，核对公开入口 `aroundMethod` 的调用方。

## 回归

本工件没有独立测试类。修改公开接口后，另选实际消费模块执行行为回归。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-logs -am verify
```
