# Javadoc 补全报告 batch-13

| 路径 | 处理符号数 | 状态 |
|---|---|---|
| ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/annotation/SpringDddAnnotationFusionTest.java | 3 | 已补（TestDomainService、TestDomainRepository、AggregateRoot） |
| ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/command/SpringCommandBusTest.java | 6 | 已存在（跳过） |
| ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/cqrs/MicrometerProjectionMetricsTest.java | 3 | 已补（OnRunCompletedTests、OnRunFailedTests、OnRunStartedTests） |
| ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/cqrs/SpringCqrsRuntimeContractTest.java | 1 | 已存在（跳过） |
| ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/cqrs/SpringJpaViewManagerTest.java | 1 | 已补（GetProjectionStatusTests） |
| ddd4j-runtime/ddd4j-runtime-testkit/src/main/java/io/ddd4j/runtime/testkit/AbstractCqrsRuntimeContractTest.java | 5 | 已存在（跳过） |
| ddd4j-runtime/ddd4j-runtime-testkit/src/main/java/io/ddd4j/runtime/testkit/CqrsRuntimeContract.java | 6 | 已存在（跳过） |
| ddd4j-runtime/ddd4j-runtime-testkit/src/main/java/io/ddd4j/runtime/testkit/RuntimeContract.java | 4 | 已补（start、services、readiness、close） |
| ddd4j-runtime/ddd4j-runtime-vertx/src/main/java/io/ddd4j/vertx/VertxDomainEventPublisher.java | 1 | 已补（field ADDRESS） |
| ddd4j-runtime/ddd4j-runtime-vertx/src/test/java/io/ddd4j/vertx/VertxDomainEventPublisherTest.java | 1 | 已补（record LocalEvent） |
| ddd4j-runtime/ddd4j-runtime-vertx/src/test/java/io/ddd4j/vertx/cqrs/VertxCqrsRuntimeContractTest.java | 2 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-dropwizard-cqrs/src/main/java/io/ddd4j/sample/dropwizard/cqrs/DropwizardCqrsApplication.java | 7 | 已补（EVENT_STORE、ORDER_REPO、COMMAND_HANDLER、COMMAND_BUS、READ_VIEW、CHUNK_READER、VIEW_MANAGER） |
| ddd4j-samples/ddd4j-sample-dropwizard-cqrs/src/main/java/io/ddd4j/sample/dropwizard/cqrs/repository/EventSourcingOrderRepository.java | 1 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-dropwizard-cqrs/src/main/java/io/ddd4j/sample/dropwizard/cqrs/web/OrderResource.java | 1 | 已补（record CreateOrderRequest） |
| ddd4j-samples/ddd4j-sample-dropwizard/src/main/java/io/ddd4j/sample/dropwizard/DropwizardOrderResource.java | 3 | 已补（CreateOrderRequest、AddOrderLineRequest、record TokenResponse） |
| ddd4j-samples/ddd4j-sample-helidon-cqrs/src/main/java/io/ddd4j/sample/helidon/cqrs/repository/EventSourcingOrderRepository.java | 1 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-helidon-cqrs/src/main/java/io/ddd4j/sample/helidon/cqrs/web/OrderResource.java | 1 | 已补（record CreateOrderRequest） |
| ddd4j-samples/ddd4j-sample-helidon/src/main/java/io/ddd4j/sample/helidon/HelidonOrderResource.java | 3 | 已补（CreateOrderRequest、AddOrderLineRequest、record TokenResponse） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/JavalinCqrsApplication.java | 1 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/cache/GoodsCacheService.java | 2 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/cache/OrderCacheService.java | 3 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/event/OrderEventListener.java | 5 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/goods/domain/GoodsRepository.java | 2 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/goods/domain/GoodsStatus.java | 4 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/goods/web/GoodsController.java | 2 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/order/application/OrderApplicationService.java | 2 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/order/domain/model/OrderStatus.java | 4 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/order/domain/repository/OrderRepository.java | 2 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/order/web/OrderCQRSQueryController.java | 1 | 已存在（跳过） |
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/main/java/io/ddd4j/sample/javalin/cqrs/order/web/OrderController.java | 3 | 已存在（跳过） |

批次合计：30 个文件 | 81 个符号（已补 28，已存在跳过 53）| 逐文件复查 81/81 通过，0 缺失。

备注：
- 本批次清单无 HAZARD 行。
- 未改动任何代码逻辑、签名与 import，仅新增/保留 javadoc 注释。
