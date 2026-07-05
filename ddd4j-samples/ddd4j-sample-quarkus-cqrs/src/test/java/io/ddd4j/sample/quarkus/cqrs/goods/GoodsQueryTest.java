package io.ddd4j.sample.quarkus.cqrs.goods;

import io.ddd4j.sample.quarkus.cqrs.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.Goods;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsId;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsQuery;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsRepository;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * 商品 CQRS 集成测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>命令端 {@code /goods} 端点（POST/PUT/DELETE/GET）</li>
 *   <li>查询端 {@code /query/goods/*}（page/list/count 充血查询）</li>
 *   <li>缓存增强查询端 {@code /api/goods/query/*}（GoodsReadResource）</li>
 *   <li>缓存命中验证：{@code /api/goods/query/cache-stats}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@QuarkusTest
class GoodsQueryTest {

    @Inject
    GoodsApplicationService applicationService;

    @Inject
    GoodsRepository repository;

    // ========== 应用服务 ==========

    @Test
    void shouldCreateUpdateChangeStatusAndDelete() {
        Goods created = applicationService.create("CQRSSKU-1", "CRUD Goods",
                new BigDecimal("199.00"), 10);
        assertThat(created.id()).isNotNull();
        assertThat(created.getCode()).isEqualTo("CQRSSKU-1");
        assertThat(created.getStatus()).isEqualTo(GoodsStatus.DRAFT);

        Goods updated = applicationService.update(GoodsId.of(created.id()),
                "CRUD Goods v2", new BigDecimal("299.00"));
        assertThat(updated.getName()).isEqualTo("CRUD Goods v2");

        Goods onSale = applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.ON_SALE);
        assertThat(onSale.getStatus()).isEqualTo(GoodsStatus.ON_SALE);

        applicationService.delete(GoodsId.of(created.id()));
        assertThat(repository.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(GoodsStatus.DELETED);
    }

    @Test
    void shouldSupportRichQueryPageListCount() {
        applicationService.create("CQRSSKU-2", "Apple X", new BigDecimal("5999"), 5);
        applicationService.create("CQRSSKU-3", "Apple Y", new BigDecimal("3999"), 5);
        applicationService.create("CQRSSKU-4", "Samsung Z", new BigDecimal("4999"), 5);

        GoodsQuery q = new GoodsQuery().setNameLike("Apple");
        q.setCurrent(1L);
        q.setSize(10L);
        assertThat(applicationService.pageQuery(q).getRecords()).hasSize(2);
        assertThat(applicationService.listQuery(new GoodsQuery().setStatus(GoodsStatus.DRAFT)))
                .hasSizeGreaterThanOrEqualTo(3);
        assertThat(applicationService.countQuery(new GoodsQuery().setNameLike("Apple"))).isEqualTo(2L);
    }

    @Test
    void shouldGetByIdAndByCode() {
        Goods created = applicationService.create("CQRSSKU-GBC", "Lookup Me",
                new BigDecimal("19.99"), 1);
        Goods byId = applicationService.getById(GoodsId.of(created.id()));
        Goods byCode = applicationService.getByCode("CQRSSKU-GBC");
        assertThat(byId.getCode()).isEqualTo("CQRSSKU-GBC");
        assertThat(byCode.id()).isEqualTo(created.id());
    }

    // ========== 命令端 REST ==========

    @Test
    void shouldCreateGoodsViaRest() {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "CQRSSKU-REST-1");
        body.put("name", "REST CQRS Goods");
        body.put("price", 199.00);
        body.put("stock", 50);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/goods")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.id", notNullValue())
                .body("data.code", equalTo("CQRSSKU-REST-1"));
    }

    @Test
    void shouldUpdateGoodsViaRest() {
        Goods created = applicationService.create("CQRSSKU-UPD", "Original",
                new BigDecimal("9.99"), 1);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Updated");
        body.put("price", 19.99);
        given().contentType(ContentType.JSON).body(body)
                .when().put("/goods/{id}", created.id())
                .then()
                .statusCode(200)
                .body("data.name", equalTo("Updated"));
    }

    @Test
    void shouldChangeStatusViaRest() {
        Goods created = applicationService.create("CQRSSKU-STA", "Status",
                new BigDecimal("1"), 1);
        given()
                .when().put("/goods/{id}/status?status=ON_SALE", created.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("ON_SALE"));
    }

    @Test
    void shouldChangeStatusToOffSaleViaRest() {
        Goods created = applicationService.create("CQRSSKU-OFF", "Off",
                new BigDecimal("1"), 1);
        given()
                .when().put("/goods/{id}/status?status=OFF_SALE", created.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("OFF_SALE"));
    }

    @Test
    void shouldGetByIdViaRest() {
        Goods created = applicationService.create("CQRSSKU-GID", "Get By Id",
                new BigDecimal("1"), 1);
        given()
                .when().get("/goods/{id}", created.id())
                .then()
                .statusCode(200)
                .body("data.id", equalTo(created.id().intValue()))
                .body("data.code", equalTo("CQRSSKU-GID"));
    }

    @Test
    void shouldGetByCodeViaRest() {
        applicationService.create("CQRSSKU-GBC-REST", "Get By Code",
                new BigDecimal("1"), 1);
        given()
                .when().get("/goods/by-code?code=CQRSSKU-GBC-REST")
                .then()
                .statusCode(200)
                .body("data.code", equalTo("CQRSSKU-GBC-REST"));
    }

    @Test
    void shouldDeleteGoodsViaRest() {
        Goods created = applicationService.create("CQRSSKU-DEL", "Delete Me",
                new BigDecimal("1"), 1);
        given()
                .when().delete("/goods/{id}", created.id())
                .then().statusCode(200)
                .body("code", equalTo(0));
    }

    // ========== 基础查询端 ==========

    @Test
    void shouldListViaBaseQueryEndpoint() {
        applicationService.create("CQRSSKU-Q-1", "List Query",
                new BigDecimal("1"), 1);
        given()
                .when().get("/query/goods/list?code=CQRSSKU-Q-1")
                .then()
                .statusCode(200)
                .body("data.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void shouldPageViaBaseQueryEndpoint() {
        applicationService.create("CQRSSKU-Q-2", "Page Query",
                new BigDecimal("1"), 1);
        given()
                .when().get("/query/goods/page?code=CQRSSKU-Q-2&current=1&size=5")
                .then()
                .statusCode(200)
                .body("data.total", greaterThanOrEqualTo(1));
    }

    @Test
    void shouldCountViaBaseQueryEndpoint() {
        applicationService.create("CQRSSKU-Q-3", "Count Query",
                new BigDecimal("1"), 1);
        given()
                .when().get("/query/goods/count?code=CQRSSKU-Q-3")
                .then()
                .statusCode(200)
                .body("data", equalTo(1));
    }

    // ========== 缓存增强查询端 ==========

    @Test
    void shouldGetByIdViaCachePreferredEndpoint() {
        Goods created = applicationService.create("CQRSSKU-CCP-1", "Cache Lookup",
                new BigDecimal("1"), 1);
        given()
                .when().get("/api/goods/query/by-id/{id}", created.id())
                .then()
                .statusCode(200)
                .body("data.id", equalTo(created.id().intValue()));
    }

    @Test
    void shouldGetByCodeViaCachePreferredEndpoint() {
        applicationService.create("CQRSSKU-CCP-2", "Cache by Code",
                new BigDecimal("1"), 1);
        given()
                .when().get("/api/goods/query/by-code/{code}", "CQRSSKU-CCP-2")
                .then()
                .statusCode(200)
                .body("data.code", equalTo("CQRSSKU-CCP-2"));
    }

    @Test
    void shouldReturn404ForMissingCodeOnCacheEndpoint() {
        given()
                .when().get("/api/goods/query/by-code/{code}", "does-not-exist-" + System.nanoTime())
                .then()
                .statusCode(200)
                .body("code", equalTo(404));
    }

    @Test
    void shouldListAllViaCachePreferredEndpoint() {
        applicationService.create("CQRSSKU-CCP-3", "Cache List 1",
                new BigDecimal("1"), 1);
        applicationService.create("CQRSSKU-CCP-4", "Cache List 2",
                new BigDecimal("1"), 1);
        given()
                .when().get("/api/goods/query/list")
                .then()
                .statusCode(200)
                .body("data.size()", greaterThanOrEqualTo(2));
    }

    @Test
    void shouldListByStatusViaCachePreferredEndpoint() {
        applicationService.create("CQRSSKU-CCP-5", "Status Filter",
                new BigDecimal("1"), 1);
        given()
                .queryParam("status", "DRAFT")
                .when().get("/api/goods/query/by-status")
                .then()
                .statusCode(200);
    }

    @Test
    void shouldCountAllViaCachePreferredEndpoint() {
        applicationService.create("CQRSSKU-CCP-6", "Count Cache",
                new BigDecimal("1"), 1);
        given()
                .when().get("/api/goods/query/count")
                .then()
                .statusCode(200);
    }

    @Test
    void shouldExposeCacheStats() {
        applicationService.create("CQRSSKU-CCP-7", "Cache Stats",
                new BigDecimal("1"), 1);
        given()
                .when().get("/api/goods/query/cache-stats")
                .then()
                .statusCode(200)
                .body("data.domain", equalTo("goods"))
                .body("data.detailBiz", notNullValue())
                .body("data.listBiz", notNullValue());
    }

    // ========== 综合场景 ==========

    @Test
    void shouldListByStatusChangeToOnSale() {
        Goods created = applicationService.create("CQRSSKU-CCP-8", "Status Walk",
                new BigDecimal("1"), 1);
        applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.ON_SALE);
        List<Goods> onSale = applicationService.listQuery(new GoodsQuery().setStatus(GoodsStatus.ON_SALE));
        assertThat(onSale).anyMatch(g -> g.id().equals(created.id()));
    }

    @Test
    void shouldReflectStatusChangeOnListQuery() {
        Goods created = applicationService.create("CQRSSKU-CCP-9", "Walk 2",
                new BigDecimal("1"), 1);
        applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.OFF_SALE);
        List<Goods> offSale = applicationService.listQuery(new GoodsQuery().setStatus(GoodsStatus.OFF_SALE));
        assertThat(offSale).anyMatch(g -> g.id().equals(created.id()));
    }

    @Test
    void shouldNotIncludeDeletedItemsInListByDraftStatus() {
        Goods created = applicationService.create("CQRSSKU-DEL-2", "Walk 3",
                new BigDecimal("1"), 1);
        applicationService.delete(GoodsId.of(created.id()));
        List<Goods> drafts = applicationService.listQuery(new GoodsQuery().setStatus(GoodsStatus.DRAFT));
        assertThat(drafts).noneMatch(g -> g.id().equals(created.id()));
    }

    @Test
    void shouldRoundTripViaCommandAndCacheQuery() {
        Goods created = applicationService.create("CQRSSKU-RT", "Round Trip",
                new BigDecimal("1"), 1);
        // 命令端
        Goods fetched = applicationService.getById(GoodsId.of(created.id()));
        assertThat(fetched.getCode()).isEqualTo("CQRSSKU-RT");
        // 缓存查询端
        given()
                .when().get("/api/goods/query/by-id/{id}", created.id())
                .then().statusCode(200);
    }

    @Test
    void shouldRejectUpdateForDeletedProduct() {
        Goods created = applicationService.create("CQRSSKU-RD", "Reject After Delete",
                new BigDecimal("1"), 1);
        applicationService.delete(GoodsId.of(created.id()));
        org.junit.jupiter.api.Assertions.assertThrows(io.ddd4j.core.exception.BizRuntimeException.class,
                () -> applicationService.update(GoodsId.of(created.id()), "X", new BigDecimal("1")));
    }
}
