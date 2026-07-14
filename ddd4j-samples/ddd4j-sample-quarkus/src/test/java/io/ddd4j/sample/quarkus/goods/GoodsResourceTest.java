package io.ddd4j.sample.quarkus.goods;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.sample.quarkus.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.goods.domain.*;
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
import static org.hamcrest.Matchers.*;

/**
 * 商品资源 Quarkus 集成测试（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>CRUD 全流程（命令端：{@code POST/PUT/DELETE/GET /goods}）</li>
 *   <li>充血 Query {@code page/list/count}（{@code GET /query/goods/*}）</li>
 *   <li>业务异常：商品编码重复、价格非法、库存负数、状态非法</li>
 *   <li>REST 端点：所有命令端 + 查询端点</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@QuarkusTest
class GoodsResourceTest {

    @Inject
    GoodsRepository repository;

    @Inject
    GoodsApplicationService applicationService;

    // ========== 应用服务层 ==========

    @Test
    void shouldCreateUpdateChangeStatusAndDelete() {
        Goods created = applicationService.create("SKU-T-1", "Test Goods", new BigDecimal("99.00"), 50);
        assertThat(created.id()).isNotNull();
        assertThat(created.getCode()).isEqualTo("SKU-T-1");
        assertThat(created.getStatus()).isEqualTo(GoodsStatus.DRAFT);

        Goods updated = applicationService.update(GoodsId.of(created.id()),
                "Test Goods v2", new BigDecimal("129.00"));
        assertThat(updated.getName()).isEqualTo("Test Goods v2");
        assertThat(updated.getPrice()).isEqualByComparingTo("129.00");

        Goods onSale = applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.ON_SALE);
        assertThat(onSale.getStatus()).isEqualTo(GoodsStatus.ON_SALE);

        applicationService.delete(GoodsId.of(created.id()));
        Goods afterDelete = repository.findById(created.id()).orElseThrow();
        assertThat(afterDelete.getStatus()).isEqualTo(GoodsStatus.DELETED);
    }

    @Test
    void shouldSupportRichQueryPageListCount() {
        applicationService.create("SKU-T-2", "Apple iPhone", new BigDecimal("5999.00"), 10);
        applicationService.create("SKU-T-3", "Apple iPad", new BigDecimal("3999.00"), 20);
        applicationService.create("SKU-T-4", "Samsung Galaxy", new BigDecimal("4999.00"), 30);

        GoodsQuery query = new GoodsQuery().setNameLike("Apple");
        query.setCurrent(1L);
        query.setSize(10L);
        Page<Goods> page = applicationService.pageQuery(query);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(2L);

        GoodsQuery onSaleQuery = new GoodsQuery().setStatus(GoodsStatus.DRAFT);
        List<Goods> drafts = applicationService.listQuery(onSaleQuery);
        assertThat(drafts).hasSizeGreaterThanOrEqualTo(3);

        long count = applicationService.countQuery(new GoodsQuery().setNameLike("Apple"));
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void shouldRejectDuplicateCode() {
        applicationService.create("SKU-DUP-1", "First", new BigDecimal("10.00"), 1);
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("SKU-DUP-1", "Second", new BigDecimal("10.00"), 1));
    }

    @Test
    void shouldRejectBlankCode() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("", "NoCode", new BigDecimal("10.00"), 1));
    }

    @Test
    void shouldRejectBlankName() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("SKU-NM-1", "", new BigDecimal("10.00"), 1));
    }

    @Test
    void shouldRejectNegativePrice() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("SKU-NEG", "Bad", new BigDecimal("-1.00"), 1));
    }

    @Test
    void shouldRejectNegativeStock() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("SKU-STK", "Bad", new BigDecimal("10.00"), -1));
    }

    @Test
    void shouldRejectUpdateForMissingProduct() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.update(GoodsId.of(99999999999L), "X", new BigDecimal("1")));
    }

    @Test
    void shouldRejectChangeStatusForDeletedProduct() {
        Goods created = applicationService.create("SKU-DEL-1", "Del me", new BigDecimal("10.00"), 5);
        applicationService.delete(GoodsId.of(created.id()));
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.ON_SALE));
    }

    @Test
    void shouldRejectUpdateForDeletedProduct() {
        Goods created = applicationService.create("SKU-DEL-2", "Del me 2", new BigDecimal("10.00"), 5);
        applicationService.delete(GoodsId.of(created.id()));
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.update(GoodsId.of(created.id()), "NewName", new BigDecimal("1")));
    }

    @Test
    void shouldChangeStatusBetweenAllStates() {
        Goods created = applicationService.create("SKU-CHG-1", "State Walker", new BigDecimal("10.00"), 1);

        Goods draft = applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.DRAFT);
        assertThat(draft.getStatus()).isEqualTo(GoodsStatus.DRAFT);

        Goods onSale = applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.ON_SALE);
        assertThat(onSale.getStatus()).isEqualTo(GoodsStatus.ON_SALE);

        Goods offSale = applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.OFF_SALE);
        assertThat(offSale.getStatus()).isEqualTo(GoodsStatus.OFF_SALE);
    }

    @Test
    void shouldGetByIdAndByCode() {
        Goods created = applicationService.create("SKU-GBC-1", "Lookup", new BigDecimal("10.00"), 1);

        Goods byId = applicationService.getById(GoodsId.of(created.id()));
        assertThat(byId.getCode()).isEqualTo("SKU-GBC-1");

        Goods byCode = applicationService.getByCode("SKU-GBC-1");
        assertThat(byCode.id()).isEqualTo(created.id());
    }

    @Test
    void shouldRejectGetByMissingId() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.getById(GoodsId.of(88888888888L)));
    }

    @Test
    void shouldRejectGetByMissingCode() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.getByCode("DOES-NOT-EXIST"));
    }

    @Test
    void shouldPageQueryWithPriceRange() {
        applicationService.create("SKU-PR-1", "Cheap", new BigDecimal("10.00"), 1);
        applicationService.create("SKU-PR-2", "Mid", new BigDecimal("100.00"), 1);
        applicationService.create("SKU-PR-3", "Pricey", new BigDecimal("1000.00"), 1);

        GoodsQuery q = new GoodsQuery().setPriceMin(new BigDecimal("50.00"))
                .setPriceMax(new BigDecimal("500.00"));
        List<Goods> result = applicationService.listQuery(q);
        assertThat(result).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldCountQueryWithStatus() {
        applicationService.create("SKU-CS-1", "StA", new BigDecimal("1.00"), 1);
        long count = applicationService.countQuery(new GoodsQuery().setStatus(GoodsStatus.DRAFT));
        assertThat(count).isGreaterThanOrEqualTo(1L);
    }

    // ========== REST 端点 ==========

    @Test
    void shouldCreateGoodsViaRest() {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "SKU-REST-1");
        body.put("name", "REST Goods");
        body.put("price", 199.00);
        body.put("stock", 100);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/goods")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.id", notNullValue())
                .body("data.code", equalTo("SKU-REST-1"))
                .body("data.status", equalTo("DRAFT"));
    }

    @Test
    void shouldUpdateGoodsViaRest() {
        Goods created = applicationService.create("SKU-REST-2", "Original", new BigDecimal("10.00"), 5);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Updated");
        body.put("price", 20.00);

        given().contentType(ContentType.JSON).body(body)
                .when().put("/goods/{id}", created.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.name", equalTo("Updated"))
                .body("data.price", equalTo(20.00F));
    }

    @Test
    void shouldChangeStatusViaRest() {
        Goods created = applicationService.create("SKU-REST-3", "For Status", new BigDecimal("10.00"), 5);

        given()
                .when().put("/goods/{id}/status?status=ON_SALE", created.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.status", equalTo("ON_SALE"));
    }

    @Test
    void shouldChangeStatusToOffSale() {
        Goods created = applicationService.create("SKU-REST-3-OFF", "Off me", new BigDecimal("10.00"), 5);

        given()
                .when().put("/goods/{id}/status?status=OFF_SALE", created.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("OFF_SALE"));
    }

    @Test
    void shouldGetByIdViaRest() {
        Goods created = applicationService.create("SKU-REST-4", "Get By Id", new BigDecimal("10.00"), 5);

        given()
                .when().get("/goods/{id}", created.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.id", equalTo(created.id().intValue()))
                .body("data.code", equalTo("SKU-REST-4"));
    }

    @Test
    void shouldGetByCodeViaRest() {
        applicationService.create("SKU-REST-5", "Get By Code", new BigDecimal("10.00"), 5);

        given()
                .when().get("/goods/by-code?code=SKU-REST-5")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.code", equalTo("SKU-REST-5"));
    }

    @Test
    void shouldDeleteViaRest() {
        Goods created = applicationService.create("SKU-REST-DEL", "To Delete", new BigDecimal("10.00"), 1);

        given()
                .when().delete("/goods/{id}", created.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0));

        Goods afterDelete = repository.findById(created.id()).orElseThrow();
        assertThat(afterDelete.getStatus()).isEqualTo(GoodsStatus.DELETED);
    }

    @Test
    void shouldListViaQueryEndpoint() {
        applicationService.create("SKU-REST-6", "List Query", new BigDecimal("10.00"), 5);

        given()
                .when().get("/query/goods/list?code=SKU-REST-6")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.size()", greaterThanOrEqualTo(1))
                .body("data[0].code", equalTo("SKU-REST-6"));
    }

    @Test
    void shouldPageViaQueryEndpoint() {
        applicationService.create("SKU-REST-7", "Page Query", new BigDecimal("10.00"), 5);

        given()
                .when().get("/query/goods/page?code=SKU-REST-7&current=1&size=5")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.records.size()", greaterThanOrEqualTo(1))
                .body("data.total", greaterThanOrEqualTo(1))
                .body("data.current", equalTo(1));
    }

    @Test
    void shouldCountViaQueryEndpoint() {
        applicationService.create("SKU-REST-8", "Count Query", new BigDecimal("10.00"), 5);

        given()
                .when().get("/query/goods/count?code=SKU-REST-8")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data", equalTo(1));
    }

    @Test
    void shouldReturnEmptyListForUnmatchedQuery() {
        given()
                .when().get("/query/goods/list?code=NOTHING-EXISTS")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.size()", equalTo(0));
    }

    @Test
    void shouldReturnZeroCountForUnmatchedQuery() {
        given()
                .when().get("/query/goods/count?code=NOTHING-EXISTS")
                .then()
                .statusCode(200)
                .body("data", equalTo(0));
    }

    @Test
    void shouldReturnBadRequestOnUnknownStatus() {
        Goods created = applicationService.create("SKU-REST-BAD", "Bad", new BigDecimal("1"), 1);
        given()
                .when().put("/goods/{id}/status?status=UNKNOWN_STATE", created.id())
                .then()
                .statusCode(400)
                .body("code", equalTo(400));
    }

    @Test
    void shouldRejectRestCreateWithDuplicateCode() {
        applicationService.create("SKU-REST-DUPLICATE", "First", new BigDecimal("1"), 1);
        Map<String, Object> body = new HashMap<>();
        body.put("code", "SKU-REST-DUPLICATE");
        body.put("name", "Second");
        body.put("price", 1.0);
        body.put("stock", 1);
        given().contentType(ContentType.JSON).body(body)
                .when().post("/goods")
                .then()
                .statusCode(500);
    }

    @Test
    void shouldPageQueryWithOrderBys() {
        applicationService.create("SKU-REST-OB-1", "Order1", new BigDecimal("10"), 1);
        applicationService.create("SKU-REST-OB-2", "Order2", new BigDecimal("20"), 1);

        given()
                .when().get("/query/goods/page?code=SKU-REST-OB-1&current=1&size=10")
                .then()
                .statusCode(200)
                .body("code", equalTo(0));
    }
}
