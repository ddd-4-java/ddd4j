package io.ddd4j.sample.quarkus.satoken;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CatalogTradeFixture {
    static final String ORDERS = "/orders/catalog/orders/";
    static final String TENANT = "quarkus-auth";
    static final Map<String, Object> ORDER = ImmutableMap.of("shopId", 7,
            "items", ImmutableList.of(ImmutableMap.of("skuCode", "SKU-1", "quantity", 2)));

    private CatalogTradeFixture() {
    }

    static void seed(CatalogApi catalog) {
        io.ddd4j.sample.order.adapter.http.Reply result = catalog.createGoods(TENANT, "BOOK", "{\"spuName\":\"Book\",\"skuCode\":\"SKU-1\",\"skuName\":\"Edition\",\"shopId\":7,\"salePriceCent\":1250}\n");
        assertTrue(result.getStatus() == 201 || result.getStatus() == 409, result.getBody());
    }

    static RequestSpecification given() {
        return io.restassured.RestAssured.given().header("X-Tenant-Id", TENANT);
    }
}
