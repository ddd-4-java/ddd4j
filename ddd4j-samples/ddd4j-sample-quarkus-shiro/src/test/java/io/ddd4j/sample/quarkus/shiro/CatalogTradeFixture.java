package io.ddd4j.sample.quarkus.shiro;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u4e3a HTTP \u5408\u540c\u6d4b\u8bd5\u521b\u5efa\u5546\u54c1\u548c\u8ba2\u5355\u6570\u636e\u5e76\u6301\u6709\u9694\u79bb\u6570\u636e\u5e93\u8d44\u6e90\u3002 */
final class CatalogTradeFixture {
    /** \u7528\u4e8e\u533a\u5206\u8ba2\u5355\u8def\u7531\u3001\u6d88\u606f\u4e3b\u9898\u6216\u6295\u5f71\u4e8b\u4ef6\u6d41\u7684\u6d4b\u8bd5\u540d\u79f0\u3002 */
    static final String ORDERS = "/orders/catalog/orders/";
    /** \u7528\u4f8b\u67e5\u8be2\u6216\u5199\u5165\u6570\u636e\u65f6\u4f7f\u7528\u7684\u79df\u6237\u6807\u8bc6\u3002 */
    static final String TENANT = "quarkus-auth";
    /** \u5f53\u524d\u5408\u540c\u6216\u4ed3\u50a8\u64cd\u4f5c\u6240\u4f7f\u7528\u7684\u8ba2\u5355\u6837\u672c\u3002 */
    static final Map<String, Object> ORDER = Map.of("shopId", 7,
            "items", List.of(Map.of("skuCode", "SKU-1", "quantity", 2)));

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>CatalogTradeFixture</code> \u5b9e\u4f8b\u3002 */
    private CatalogTradeFixture() {
    }

    /**
     * \u8c03\u7528 <code>catalog.createGoods()</code>\u3001<code>result.getStatus()</code>\u3001<code>result.getBody()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param catalog \u4f20\u7ed9 <code>catalog.createGoods()</code> \u7684 <code>CatalogApi</code> \u8f93\u5165
     */
    static void seed(CatalogApi catalog) {
        var result = catalog.createGoods(TENANT, "BOOK", """
                {"spuName":"Book","skuCode":"SKU-1","skuName":"Edition","shopId":7,"salePriceCent":1250}
                """);
        assertTrue(result.getStatus() == 201 || result.getStatus() == 409, result.getBody());
    }

    /**
     * \u901a\u8fc7 <code>io.restassured.RestAssured.given()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>io.restassured.RestAssured.given().header("X-Tenant-Id", TENANT)</code>
     */
    static RequestSpecification given() {
        return io.restassured.RestAssured.given().header("X-Tenant-Id", TENANT);
    }
}
