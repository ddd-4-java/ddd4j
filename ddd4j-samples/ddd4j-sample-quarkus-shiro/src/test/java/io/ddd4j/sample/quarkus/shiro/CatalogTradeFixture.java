package io.ddd4j.sample.quarkus.shiro;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 为 HTTP 合同测试创建商品和订单数据并持有隔离数据库资源。
 */
final class CatalogTradeFixture {
    /**
     * 用于区分订单路由、消息主题或投影事件流的测试名称。
     */
    static final String ORDERS = "/orders/catalog/orders/";
    /**
     * 用例查询或写入数据时使用的租户标识。
     */
    static final String TENANT = "quarkus-auth";
    /**
     * 当前合同或仓储操作所使用的订单样本。
     */
    static final Map<String, Object> ORDER = Map.of("shopId", 7,
            "items", List.of(Map.of("skuCode", "SKU-1", "quantity", 2)));

    /**
     * 限制调用方直接创建 <code>CatalogTradeFixture</code> 实例。
     */
    private CatalogTradeFixture() {
    }

    /**
     * 调用 <code>catalog.createGoods()</code>、<code>result.getStatus()</code>、<code>result.getBody()</code> 执行当前夹具操作。
     *
     * @param catalog 传给 <code>catalog.createGoods()</code> 的 <code>CatalogApi</code> 输入
     */
    static void seed(CatalogApi catalog) {
        var result = catalog.createGoods(TENANT, "BOOK", """
                {"spuName":"Book","skuCode":"SKU-1","skuName":"Edition","shopId":7,"salePriceCent":1250}
                """);
        assertTrue(result.getStatus() == 201 || result.getStatus() == 409, result.getBody());
    }

    /**
     * 通过 <code>io.restassured.RestAssured.given()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>io.restassured.RestAssured.given().header("X-Tenant-Id", TENANT)</code>
     */
    static RequestSpecification given() {
        return io.restassured.RestAssured.given().header("X-Tenant-Id", TENANT);
    }
}
