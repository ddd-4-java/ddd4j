# Javadoc 补全批次报告 batch-10

| 路径 | 处理符号数 | 状态 |
| --- | --- | --- |
| ddd4j-extensions/ddd4j-extension-qrcode/src/main/java/io/ddd4j/extension/qrcode/template/QrCodeImageElement.java | 1 | 已补 1（type class Builder） |
| ddd4j-extensions/ddd4j-extension-qrcode/src/main/java/io/ddd4j/extension/qrcode/template/QrCodeTemplateRegistry.java | 3 | 已补 3（register/find/remove） |
| ddd4j-extensions/ddd4j-extension-qrcode/src/main/java/io/ddd4j/extension/qrcode/template/QrCodeTextElement.java | 1 | 已补 1（type class Builder） |
| ddd4j-extensions/ddd4j-extension-validation/src/main/java/io/ddd4j/extension/validation/FileContentCheckProvider.java | 2 | 已有 javadoc，跳过（supports/check） |
| ddd4j-extensions/ddd4j-extension-validation/src/main/java/io/ddd4j/extension/validation/FileTypeDetector.java | 1 | 已有 javadoc，跳过（detect） |
| ddd4j-extensions/ddd4j-extension-validation/src/main/java/io/ddd4j/extension/validation/FileValidationFailure.java | 8 | 已补 8（8 个枚举常量） |
| ddd4j-extensions/ddd4j-extension-validation/src/main/java/io/ddd4j/extension/validation/FileValidationPolicy.java | 1 | 已有 javadoc，跳过（type class Builder） |
| ddd4j-extensions/ddd4j-extension-validation/src/main/java/io/ddd4j/extension/validation/FileValidationService.java | 1 | 已补 1（type class ListSupport） |
| ddd4j-extensions/ddd4j-extension-validation/src/main/java/io/ddd4j/extension/validation/InputStreamSource.java | 1 | 已有 javadoc，跳过（openStream） |
| ddd4j-extensions/ddd4j-extension-validation/src/main/java/io/ddd4j/extension/validation/ValidatableFile.java | 4 | 已有 javadoc，跳过（fileName/contentType/size/openStream） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/lang/FastdfsKit.java | 1 | 已补 1（field g_charset） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/lang/FunctionKit.java | 10 | 已补 10（10 个转换函数字段） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/lang/IdKit.java | 1 | 已补 1（type record SnowflakeOptions） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/lang/JsonKit.java | 8 | 已补 6、已有跳过 2（YYYYMMDDHHMMSS/STRING_TYPE/INTEGER_TYPE/LONG_TYPE/DOUBLE_TYPE/BaseSimpleDateFormat 已补；REDIS_OBJECT_MAPPER/DEFAULT_OBJECT_MAPPER 已有） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/lang/LotteryKit.java | 1 | 已补 1（type class LotteryElement） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/lang/NumKit.java | 1 | 已补 1（field ZERO） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/lang/RankKit.java | 1 | 已有 javadoc，跳过（type class RankModel） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/lang/RankSortTypeEnum.java | 3 | 已有 javadoc，跳过（3 个枚举常量） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/lang/StrKit.java | 2 | 已补 2（field unknowChar；field CONFIG_LOCATION_DELIMITERS 由普通注释改为中文 javadoc） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/matcher/AntPathMatcher.java | 5 | 已有 javadoc，跳过（DEFAULT_PATH_SEPARATOR 与 4 个嵌套类型） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/matcher/PathMatcher.java | 7 | 已有 javadoc，跳过（7 个接口方法） |
| ddd4j-kit/src/main/java/io/ddd4j/kit/web/IpKit.java | 1 | 已补 1（field HOST_ADDRESS） |
| ddd4j-kit/src/test/java/io/ddd4j/kit/lang/BeanKitTest.java | 2 | 已补 2（type class SourceBean/TargetBean） |
| ddd4j-metrics/src/test/java/io/ddd4j/metrics/OpenTelemetryProjectionMetricsTest.java | 3 | 已补 3（OnRunStartedTests/OnRunCompletedTests/OnRunFailedTests） |
| ddd4j-mq/ddd4j-mq-activemq/src/main/java/io/ddd4j/mq/activemq/ActiveMQAcknowledgment.java | 5 | 已有 javadoc，跳过（3 个字段、type interface IoOperation、run） |
| ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/BrokerType.java | 14 | 已补 11、已有跳过 3（DISRUPTOR/MQTT/MQTT_MICA 已有） |
| ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/MQClient.java | 20 | 已补 6（impl 补描述、close/failure/initializationException/rollback/unregisterPublisher）、已有跳过 14 |
| ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/delivery/MQDeliveryHeaders.java | 1 | 已有 javadoc，跳过（field MESSAGE_ID） |
| ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/delivery/MQDeliveryObserver.java | 7 | 已有 javadoc，跳过（7 个回调方法） |
| ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/delivery/MQInboxStore.java | 1 | 已有 javadoc，跳过（recordIfAbsent） |

批次合计：30 个文件，117 个符号（其中新补 javadoc 59 处，已有完整 javadoc 跳过 58 处），HAZARD 行 0 条；逐文件复查 117 个符号上方均存在 javadoc 块。
