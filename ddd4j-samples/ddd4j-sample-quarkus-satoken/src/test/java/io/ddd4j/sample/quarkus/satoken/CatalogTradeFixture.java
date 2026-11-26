package io.ddd4j.sample.quarkus.satoken;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CatalogTradeFixture {
    static final String ORDERS = "/orders/catalog/orders/";
    static final String TENANT = "quarkus-auth";
    static final Map<String, Object> ORDER = Map.of("shopId", 7,
            "items", List.of(Map.of("skuCode", "SKU-1", "quantity", 2)));

    private CatalogTradeFixture() {
    }

    static void seed(CatalogApi catalog) {
        var result = catalog.createGoods(TENANT, "BOOK", """
                {"spuName":"Book","skuCode":"SKU-1","skuName":"Edition","shopId":7,"salePriceCent":1250}
                """);
        assertTrue(result.getStatus() == 201 || result.getStatus() == 409, result.getBody());
    }

    static RequestSpecification given() {
        return io.restassured.RestAssured.given().header("X-Tenant-Id", TENANT);
    }
}
