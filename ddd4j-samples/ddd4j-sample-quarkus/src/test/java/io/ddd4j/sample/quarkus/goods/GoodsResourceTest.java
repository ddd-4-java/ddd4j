/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.quarkus.goods;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.sample.quarkus.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.goods.domain.*;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import javax.inject.Inject;
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
@io.quarkus.test.junit.TestProfile(io.ddd4j.sample.quarkus.GoodsStandaloneProfile.class)
class GoodsResourceTest {

    /** \u7528\u4e8e\u5f53\u524d\u8bfb\u53d6\u6216\u5199\u5165\u65ad\u8a00\u7684\u6d4b\u8bd5\u4ed3\u50a8\u3002 */
    @Inject
    GoodsRepository repository;

    /** \u5904\u7406\u5546\u54c1\u521b\u5efa\u3001\u7ef4\u62a4\u548c\u67e5\u8be2\u64cd\u4f5c\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    @Inject
    GoodsApplicationService applicationService;

    // ========== 应用服务层 ==========

    /** \u9a8c\u8bc1<code>created.id()</code>\u975e\u7a7a\uff0c<code>created.getCode()</code> \u7b49\u4e8e <code>"SKU-T-1"</code>\uff0c<code>afterDelete.getStatus()</code> \u7b49\u4e8e <code>GoodsStatus.DELETED</code>\u3002 */
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
        Goods afterDelete = repository.findById(created.id()).orElseThrow(IllegalStateException::new);
        assertThat(afterDelete.getStatus()).isEqualTo(GoodsStatus.DELETED);
    }

    /** \u9a8c\u8bc1<code>page.getRecords()</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>page.getTotal()</code> \u7b49\u4e8e <code>2L</code>\uff0c<code>count</code> \u7b49\u4e8e <code>2L</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectDuplicateCode() {
        applicationService.create("SKU-DUP-1", "First", new BigDecimal("10.00"), 1);
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("SKU-DUP-1", "Second", new BigDecimal("10.00"), 1));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectBlankCode() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("", "NoCode", new BigDecimal("10.00"), 1));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectBlankName() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("SKU-NM-1", "", new BigDecimal("10.00"), 1));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectNegativePrice() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("SKU-NEG", "Bad", new BigDecimal("-1.00"), 1));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectNegativeStock() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.create("SKU-STK", "Bad", new BigDecimal("10.00"), -1));
    }

    /** \u9a8c\u8bc1<code>applicationService.update(GoodsId.of(99999999999L), "X", new BigDecimal("1"))</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectUpdateForMissingProduct() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.update(GoodsId.of(99999999999L), "X", new BigDecimal("1")));
    }

    /** \u9a8c\u8bc1<code>applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.ON_SALE)</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectChangeStatusForDeletedProduct() {
        Goods created = applicationService.create("SKU-DEL-1", "Del me", new BigDecimal("10.00"), 5);
        applicationService.delete(GoodsId.of(created.id()));
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.changeStatus(GoodsId.of(created.id()), GoodsStatus.ON_SALE));
    }

    /** \u9a8c\u8bc1<code>applicationService.update(GoodsId.of(created.id()), "NewName", new BigDecimal("1"))</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectUpdateForDeletedProduct() {
        Goods created = applicationService.create("SKU-DEL-2", "Del me 2", new BigDecimal("10.00"), 5);
        applicationService.delete(GoodsId.of(created.id()));
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.update(GoodsId.of(created.id()), "NewName", new BigDecimal("1")));
    }

    /** \u9a8c\u8bc1<code>draft.getStatus()</code> \u7b49\u4e8e <code>GoodsStatus.DRAFT</code>\uff0c<code>onSale.getStatus()</code> \u7b49\u4e8e <code>GoodsStatus.ON_SALE</code>\uff0c<code>offSale.getStatus()</code> \u7b49\u4e8e <code>GoodsStatus.OFF_SALE</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>byId.getCode()</code> \u7b49\u4e8e <code>"SKU-GBC-1"</code>\uff0c<code>byCode.id()</code> \u7b49\u4e8e <code>created.id()</code>\u3002 */
    @Test
    void shouldGetByIdAndByCode() {
        Goods created = applicationService.create("SKU-GBC-1", "Lookup", new BigDecimal("10.00"), 1);

        Goods byId = applicationService.getById(GoodsId.of(created.id()));
        assertThat(byId.getCode()).isEqualTo("SKU-GBC-1");

        Goods byCode = applicationService.getByCode("SKU-GBC-1");
        assertThat(byCode.id()).isEqualTo(created.id());
    }

    /** \u9a8c\u8bc1<code>applicationService.getById(GoodsId.of(88888888888L))</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectGetByMissingId() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.getById(GoodsId.of(88888888888L)));
    }

    /** \u9a8c\u8bc1<code>applicationService.getByCode("DOES-NOT-EXIST")</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void shouldRejectGetByMissingCode() {
        org.junit.jupiter.api.Assertions.assertThrows(BizRuntimeException.class,
                () -> applicationService.getByCode("DOES-NOT-EXIST"));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>applicationService.create()</code>\u3001<code>applicationService.listQuery()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>count</code> \u5927\u4e8e\u6216\u7b49\u4e8e <code>1L</code>\u3002 */
    @Test
    void shouldCountQueryWithStatus() {
        applicationService.create("SKU-CS-1", "StA", new BigDecimal("1.00"), 1);
        long count = applicationService.countQuery(new GoodsQuery().setStatus(GoodsStatus.DRAFT));
        assertThat(count).isGreaterThanOrEqualTo(1L);
    }

    // ========== REST 端点 ==========

    /** \u8bf7\u6c42 <code>POST /goods</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.status</code> \u7b49\u4e8e <code>"DRAFT"</code>\u3002 */
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

    /** \u8bf7\u6c42 <code>PUT /goods/{id}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.price</code> \u7b49\u4e8e <code>20.00F</code>\u3002 */
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

    /** \u8bf7\u6c42 <code>PUT /goods/{id}/status?status=ON_SALE</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.status</code> \u7b49\u4e8e <code>"ON_SALE"</code>\u3002 */
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

    /** \u8bf7\u6c42 <code>PUT /goods/{id}/status?status=OFF_SALE</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.status</code> \u7b49\u4e8e <code>"OFF_SALE"</code>\u3002 */
    @Test
    void shouldChangeStatusToOffSale() {
        Goods created = applicationService.create("SKU-REST-3-OFF", "Off me", new BigDecimal("10.00"), 5);

        given()
                .when().put("/goods/{id}/status?status=OFF_SALE", created.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("OFF_SALE"));
    }

    /** \u8bf7\u6c42 <code>GET /goods/{id}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.code</code> \u7b49\u4e8e <code>"SKU-REST-4"</code>\u3002 */
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

    /** \u8bf7\u6c42 <code>GET /goods/by-code?code=SKU-REST-5</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.code</code> \u7b49\u4e8e <code>"SKU-REST-5"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>afterDelete.getStatus()</code> \u7b49\u4e8e <code>GoodsStatus.DELETED</code>\u3002 */
    @Test
    void shouldDeleteViaRest() {
        Goods created = applicationService.create("SKU-REST-DEL", "To Delete", new BigDecimal("10.00"), 1);

        given()
                .when().delete("/goods/{id}", created.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0));

        Goods afterDelete = repository.findById(created.id()).orElseThrow(IllegalStateException::new);
        assertThat(afterDelete.getStatus()).isEqualTo(GoodsStatus.DELETED);
    }

    /** \u8bf7\u6c42 <code>GET /query/goods/list?code=SKU-REST-6</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>code</code> \u7b49\u4e8e <code>0</code>\u3002 */
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

    /** \u8bf7\u6c42 <code>GET /query/goods/page?code=SKU-REST-7&amp;current=1&amp;size=5</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.current</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u8bf7\u6c42 <code>GET /query/goods/count?code=SKU-REST-8</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u8bf7\u6c42 <code>GET /query/goods/list?code=NOTHING-EXISTS</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>code</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void shouldReturnEmptyListForUnmatchedQuery() {
        given()
                .when().get("/query/goods/list?code=NOTHING-EXISTS")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.size()", equalTo(0));
    }

    /** \u8bf7\u6c42 <code>GET /query/goods/count?code=NOTHING-EXISTS</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void shouldReturnZeroCountForUnmatchedQuery() {
        given()
                .when().get("/query/goods/count?code=NOTHING-EXISTS")
                .then()
                .statusCode(200)
                .body("data", equalTo(0));
    }

    /** \u8bf7\u6c42 <code>PUT /goods/{id}/status?status=UNKNOWN_STATE</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 400\uff0c\u54cd\u5e94\u4e2d\u7684 <code>code</code> \u7b49\u4e8e <code>400</code>\u3002 */
    @Test
    void shouldReturnBadRequestOnUnknownStatus() {
        Goods created = applicationService.create("SKU-REST-BAD", "Bad", new BigDecimal("1"), 1);
        given()
                .when().put("/goods/{id}/status?status=UNKNOWN_STATE", created.id())
                .then()
                .statusCode(400)
                .body("code", equalTo(400));
    }

    /** \u8bf7\u6c42 <code>POST /goods</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 500\u3002 */
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

    /** \u8bf7\u6c42 <code>GET /query/goods/page?code=SKU-REST-OB-1&amp;current=1&amp;size=10</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>code</code> \u7b49\u4e8e <code>0</code>\u3002 */
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
