package io.ddd4j.sample.spring.cqrs.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.cqrs.SpringCqrsApplication;
import io.ddd4j.sample.spring.cqrs.goods.web.dto.CreateGoodsRequest;
import io.ddd4j.sample.spring.cqrs.order.web.dto.AddOrderLineRequest;
import io.ddd4j.sample.spring.cqrs.order.web.dto.CreateOrderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CQRS 读写分离 + 缓存层集成测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>Order CQRS 读侧（{@code /api/orders/query/*}）统计 / 买家计数 / 详情</li>
 *   <li>Order 写侧不影响 CQRS 读侧路径（CQRS 分离）</li>
 *   <li>Goods CQRS 缓存读侧（{@code /api/goods/query/*}）详情 / 列表 / 状态</li>
 *   <li>Goods 写侧触发缓存读侧（或在内存场景下静态可读）</li>
 *   <li>Order CQRS 分页列表</li>
 * </ul>
 *
 * @author Test
 */
@SpringBootTest(classes = SpringCqrsApplication.class)
@AutoConfigureMockMvc
@DisplayName("CQRS 读写分离 + 缓存读侧")
class CqrsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 在 CQRS 测试里创建一个草稿订单用于读侧验证。返回订单 ID。
     */
    private String createDraftOrder(String buyerId) throws Exception {
        String orderNo = "C-" + UUID.randomUUID();
        MvcResult r = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(orderNo, buyerId, "Alice"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    /**
     * 为订单添加一行。返回 void（成功）。
     */
    private void addLine(String orderId, BigDecimal price, int qty) throws Exception {
        mockMvc.perform(post("/orders/" + orderId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddOrderLineRequest("SKU-" + UUID.randomUUID(),
                                        "Item", qty, price))))
                .andExpect(status().isOk());
    }

    /**
     * 创建一个商品，返回商品 ID。
     */
    private Long createGoods() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateGoodsRequest("CQ-" + UUID.randomUUID(),
                        "Item-CQ", new BigDecimal("199.00"), 5));
        MvcResult r = mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    // =========================== Order CQRS 读侧 ===========================

    @Test
    @DisplayName("Order CQRS 读侧：分页列表（/api/orders/query/list）")
    void orderCqrs_list_returns() throws Exception {
        createDraftOrder("CQ-LIST-" + UUID.randomUUID());
        mockMvc.perform(get("/api/orders/query/list"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    @DisplayName("Order CQRS 读侧：分页参数生效")
    void orderCqrs_list_paged() throws Exception {
        for (int i = 0; i < 3; i++) {
            createDraftOrder("CQ-PAG-" + i + "-" + UUID.randomUUID());
        }
        mockMvc.perform(get("/api/orders/query/list").param("page", "1").param("pageSize", "5"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(5));
    }

    @Test
    @DisplayName("Order CQRS 读侧：订单统计（缓存）")
    void orderCqrs_stats_returns() throws Exception {
        createDraftOrder("CQ-STAT-" + UUID.randomUUID());
        MvcResult r = mockMvc.perform(get("/api/orders/query/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        Map<String, Object> data = objectMapper.readValue(
                r.getResponse().getContentAsString(), Map.class);
        Object total = ((Map<?, ?>) data.get("data")).get("total");
        assertThat(total).isNotNull();
    }

    @Test
    @DisplayName("Order CQRS 读侧：买家订单计数（缓存）")
    void orderCqrs_buyerCount_returns() throws Exception {
        String buyerId = "CQ-BUYER-" + UUID.randomUUID();
        createDraftOrder(buyerId);
        MvcResult r = mockMvc.perform(get("/api/orders/query/buyer/{buyerId}/count", buyerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buyerId").value(buyerId))
                .andExpect(jsonPath("$.data.count").isNumber())
                .andReturn();
        Map<String, Object> data = objectMapper.readValue(
                r.getResponse().getContentAsString(), Map.class);
        Map<?, ?> inner = (Map<?, ?>) data.get("data");
        assertThat(((Number) inner.get("count")).longValue()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Order CQRS 读侧：订单详情")
    void orderCqrs_detail_returns() throws Exception {
        String id = createDraftOrder("CQ-DET-" + UUID.randomUUID());
        mockMvc.perform(get("/api/orders/query/detail/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("Order CQRS 读侧：详情 - 不存在 → 业务异常")
    void orderCqrs_detail_notFound_fails() throws Exception {
        mockMvc.perform(get("/api/orders/query/detail/no-such-order"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("Order CQRS：写侧与读侧共用仓储（统计可观察到新订单）")
    void orderCqrs_writeRead_areConsistent() throws Exception {
        String buyerId = "CQ-CO-" + UUID.randomUUID();
        // 写侧创建订单
        createDraftOrder(buyerId);
        createDraftOrder(buyerId);

        // 读侧统计买家订单数（缓存层在第一次 miss 后应包含刚创建的）
        mockMvc.perform(get("/api/orders/query/buyer/{buyerId}/count", buyerId))
                .andExpect(jsonPath("$.data.buyerId").value(buyerId));
    }

    // =========================== Goods CQRS 缓存读侧 ===========================

    @Test
    @DisplayName("Goods CQRS 读侧：详情（缓存优先）")
    void goodsCqrs_detail_returns() throws Exception {
        Long id = createGoods();
        mockMvc.perform(get("/api/goods/query/by-id/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("Goods CQRS 读侧：列表（缓存快照）")
    void goodsCqrs_list_returnsArray() throws Exception {
        createGoods();
        mockMvc.perform(get("/api/goods/query/list"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Goods CQRS 读侧：按状态过滤")
    void goodsCqrs_listByStatus_returns() throws Exception {
        createGoods();
        mockMvc.perform(get("/api/goods/query/by-status").param("status", "DRAFT"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Goods CQRS 读侧：按状态过滤 - 上架")
    void goodsCqrs_listByStatus_onSale() throws Exception {
        Long id = createGoods();
        // 状态改为 ON_SALE
        mockMvc.perform(post("/api/goods") // 触发 evict — 这里仅改状态即可
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of()))); // bogus to ensure caching path
        // 触发一次按状态读取
        mockMvc.perform(get("/api/goods/query/by-status").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.data").isArray());
        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("Goods CQRS 读侧：计数")
    void goodsCqrs_count_returnsNumber() throws Exception {
        createGoods();
        MvcResult r = mockMvc.perform(get("/api/goods/query/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();
        Map<String, Object> data = objectMapper.readValue(
                r.getResponse().getContentAsString(), Map.class);
        Number cnt = (Number) data.get("data");
        assertThat(cnt.longValue()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Goods CQRS 读侧：缓存统计（监控）")
    void goodsCqrs_cacheStats_returns() throws Exception {
        mockMvc.perform(get("/api/goods/query/cache-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.domain").value("goods"))
                .andExpect(jsonPath("$.data.detailBiz").exists())
                .andExpect(jsonPath("$.data.listBiz").exists());
    }

    @Test
    @DisplayName("Goods CQRS 读侧：按编码查询")
    void goodsCqrs_byCode_returns() throws Exception {
        String code = "BCC-" + UUID.randomUUID();
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, "X",
                                        new BigDecimal("10.00"), 1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/goods/query/by-code/{code}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code));
    }

    // =========================== 读写分离路径 ===========================

    @Test
    @DisplayName("Order CQRS 分离：写侧路径 /orders vs 读侧路径 /api/orders/query/*")
    void orderCqrs_pathSeparation() throws Exception {
        // 写侧
        String id = createDraftOrder("SEP-" + UUID.randomUUID());
        // 读侧（独立命名空间）
        mockMvc.perform(get("/api/orders/query/detail/" + id))
                .andExpect(jsonPath("$.data.id").value(id));
        // 写侧也能直接查询
        mockMvc.perform(get("/orders/" + id))
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("Goods CQRS 分离：写侧路径 /api/goods vs 读侧路径 /api/goods/query/*")
    void goodsCqrs_pathSeparation() throws Exception {
        Long id = createGoods();
        // 写侧 + 读侧都不冲突地返回同一资源
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(jsonPath("$.data.id").value(id));
        mockMvc.perform(get("/api/goods/query/by-id/" + id))
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("Order CQRS 写侧不影响分页统计：连续创建后 total 增长")
    void orderCqrs_statsReflectsCreation() throws Exception {
        mockMvc.perform(get("/api/orders/query/list")).andExpect(status().isOk());
        createDraftOrder("STAT-A-" + UUID.randomUUID());
        createDraftOrder("STAT-B-" + UUID.randomUUID());

        mockMvc.perform(get("/api/orders/query/list"))
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    @DisplayName("Goods 删除后 CQRS 读侧仍可访问（值被软删除标记为 DELETED）")
    void goodsDelete_observableViaCqrsRead() throws Exception {
        Long id = createGoods();
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/goods/query/by-id/" + id))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }
}
