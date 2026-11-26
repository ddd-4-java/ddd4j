# ddd4j-sample-quarkus

## 当前商品交易入口

此模块以 普通 DDD 方式运行共享商品交易。先按[示例总览](../README.md#启动默认订单示例)配置 MySQL。以下命令从仓库根目录运行，使用实际 Maven 版本替换 `<version>`。

```bash
module=ddd4j-samples/ddd4j-sample-quarkus
./mvnw -pl "$module" -am -DskipTests -Dmaven.javadoc.skip=true package
java -jar "$module/target/quarkus-app/quarkus-run.jar"
```

业务请求携带 `X-Tenant-Id`。商品地址为 `/api/goods/{spuCode}`，订单地址为 `/api/orders/{orderNo}`。支付、取消和履约分别使用订单子路径 `/payment`、`/cancel`、`/fulfill`。

<details>
<summary>商品交易迁移前的说明</summary>

Quarkus 通过 JAX-RS 暴露已有共享模块的商品及订单用例。

## 默认商品交易入口

默认启动原生应用时，Flyway 先迁移专用 MySQL 库。应用随后提供商品、规格、订单及成交明细的读写示例。[商品交易的数据库配置、四个接口与验证命令](../README.md#启动默认订单示例)。

## 运行

在当前 ddd4j 发布线的仓库根目录使用对应 JDK 和 Maven Wrapper。

| ddd4j 线 | JDK | Maven Wrapper | Quarkus | REST 依赖 |
|-----------|-----|---------------|---------|-----------|
| 1.0.x | 8 | 3.9.16 | 1.13.7.Final | `quarkus-resteasy-jackson` |
| 2.0.x | 17 | 3.9.16 | 3.40.1.2 | `quarkus-rest-jackson` |
| 3.0.x | 21 | 4.0.0-rc-6 | 3.38.2 | `quarkus-rest-jackson` |

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus -am package
java -jar ddd4j-samples/ddd4j-sample-quarkus/target/quarkus-app/quarkus-run.jar
python3 ddd4j-samples/scripts/check_catalog_trade.py http://localhost:8080
```

Quarkus 在启动日志中记录实际监听地址。默认端口为 8080，可在 `-jar` 前加 `-Dquarkus.http.port=8082` 使用其他端口。

### 开发模式

首次运行先将样例依赖安装到本地 Maven 仓库。

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus -am install -DskipTests
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus quarkus:dev
```

设置构建属性 `-Dsample.orders.legacy=true` 可运行以下兼容接口。

## 原订单组件

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/orders` | 创建草稿订单，返回 HTTP 201 |
| POST | `/api/orders/{id}/lines` | 添加订单行 |
| POST | `/api/orders/{id}/pay` | 支付，必须提供 `Idempotency-Key` |
| POST | `/api/orders/{id}/ship` | 发货 |
| POST | `/api/orders/{id}/cancel` | 取消订单 |
| GET | `/api/orders/{id}` | 按 ID 查询 |
| GET | `/api/orders/by-no?orderNo=ORDER-1001` | 按订单编号查询 |
| GET | `/api/orders?status=DRAFT` | 按状态查询，另支持 `buyerId`、`page`、`size` |

创建订单，从响应的 `data.id` 读取订单 ID 并赋给下方的 `ORDER_ID`。

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"ORDER-1001","buyerId":"BUYER-1","buyerName":"Alice"}'
```

```bash
curl -X POST "http://localhost:8080/api/orders/$ORDER_ID/lines" \
  -H 'Content-Type: application/json' \
  -d '{"goodsId":"SKU-1","goodsName":"DDD Book","quantity":2,"unitPrice":39.80}'

curl -X POST "http://localhost:8080/api/orders/$ORDER_ID/pay" \
  -H "Idempotency-Key: payment-$ORDER_ID"

curl "http://localhost:8080/api/orders/$ORDER_ID"
curl -X POST "http://localhost:8080/api/orders/$ORDER_ID/ship"
```

订单存储、查询模型及幂等状态均在进程内，重启后重新开始。应用内置样例装配，可直接使用共享订单服务。Web 层通过 `CacheKit` 注册 `ddd4j-web-idempotency` 本地缓存，支付同时使用共享应用服务的幂等契约。

默认 `ddd4j.quarkus.web.bearer-required=false`，可匿名使用。启动时可用 `-Dddd4j.quarkus.web.bearer-required=true` 启用 `/api/` 的 Bearer 策略。集成测试使用 `SampleSubjectProvider` 的演示令牌验证无凭证拒绝和授权访问。完整认证示例见 [Sa-Token](../ddd4j-sample-quarkus-satoken/README.md) 与 [Shiro](../ddd4j-sample-quarkus-shiro/README.md)，持久化查询示例见 [CQRS](../ddd4j-sample-quarkus-cqrs/README.md)。

## 原商品组件

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/goods` | 创建商品（自动分配 ID） |
| PUT | `/goods/{id}` | 更新商品名称和价格 |
| PUT | `/goods/{id}/status?status=ON_SALE` | 调整商品状态 |
| DELETE | `/goods/{id}` | 软删除商品 |
| GET | `/goods/{id}` | 按 ID 查询 |
| GET | `/goods/by-code?code=SKU-001` | 按编码查询 |
| GET | `/query/goods/page?status=ON_SALE&current=1&size=10&orderBys=price_DESC` | 分页和排序 |
| GET | `/query/goods/list?nameLike=iPhone` | 列表查询 |
| GET | `/query/goods/count?status=ON_SALE` | 计数 |

创建商品，从响应的 `data.id` 读取商品 ID 并赋给下方的 `GOODS_ID`。

```bash
curl -X POST http://localhost:8080/goods \
  -H 'Content-Type: application/json' \
  -d '{"code":"SKU-001","name":"iPhone 15","price":5999.00,"stock":100}'
```

```bash
curl -X PUT "http://localhost:8080/goods/$GOODS_ID" \
  -H 'Content-Type: application/json' \
  -d '{"name":"iPhone 15 Pro","price":7999.00}'

curl -X PUT "http://localhost:8080/goods/$GOODS_ID/status?status=ON_SALE"
curl "http://localhost:8080/goods/by-code?code=SKU-001"
curl "http://localhost:8080/query/goods/page?status=ON_SALE&current=1&size=10&orderBys=price_DESC"
curl "http://localhost:8080/query/goods/list?nameLike=iPhone"
curl "http://localhost:8080/query/goods/count?status=ON_SALE"
curl -X DELETE "http://localhost:8080/goods/$GOODS_ID"
```

`GoodsConfig` 在 CDI 启动阶段注册商品仓储。`GoodsQueryResource` 将 HTTP 查询参数转换为 `GoodsQuery`，再调用应用服务分页、列表或计数。支持按编码、名称、状态和价格区间过滤。排序表达式用逗号分隔，仅支持 `id`、`createTime`、`updateTime`、`price`。

## 测试

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus -am test
```

测试覆盖匿名订单流程、显式 Bearer 模式、幂等键与订单状态校验、四项核心 SPI、商品 CRUD 与过滤分页，以及 CDI 资源注册。并行验证时可用 `-Dquarkus.http.test-port=18081` 指定空闲的测试端口。

## 源码入口

- [共享订单应用服务](../ddd4j-sample-order-application)
- [Quarkus 运行时适配](../../ddd4j-runtime/ddd4j-runtime-quarkus)
- [Quarkus Web 适配](../../ddd4j-web/ddd4j-web-quarkus)
- [缓存组件](../../ddd4j-cache/README.md)

</details>
