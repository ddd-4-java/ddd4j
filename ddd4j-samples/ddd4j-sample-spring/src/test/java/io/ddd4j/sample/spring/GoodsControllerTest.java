package io.ddd4j.sample.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.goods.domain.GoodsStatus;
import io.ddd4j.sample.spring.goods.web.dto.CreateGoodsRequest;
import io.ddd4j.sample.spring.goods.web.dto.UpdateGoodsRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品 REST 写侧 + 读侧集成测试（{@code /api/goods*}）。
 *
 * <p>覆盖全部 6 个写端点 + 3 个读端点 + 状态流转 + 边界条件。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Goods REST 集成测试")
class GoodsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 创建商品并返回其 ID。 */
    private Long createGoods(String code, String name, String price, int stock) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, name, new BigDecimal(price), stock))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    // ============================ POST /api/goods ============================

    @Test
    @DisplayName("创建商品 - 成功")
    void createGoods_returnsCreatedEntity() throws Exception {
        String code = "SKU-" + UUID.randomUUID();
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, "iPhone 15", new BigDecimal("5999.00"), 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("goods created"))
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.name").value("iPhone 15"))
                .andExpect(jsonPath("$.data.price").value(5999.00))
                .andExpect(jsonPath("$.data.stock").value(100));
    }

    @Test
    @DisplayName("创建商品 - 编码重复 → 业务异常")
    void createGoods_duplicateCode_fails() throws Exception {
        String code = "SKU-" + UUID.randomUUID();
        createGoods(code, "First", "10.00", 10);

        // 重复编码
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, "Second", new BigDecimal("20.00"), 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("创建商品 - 负库存 → 业务异常")
    void createGoods_negativeStock_fails() throws Exception {
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest("SKU-NEG", "X", new BigDecimal("10.00"), -1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("创建商品 - 负价格 → 业务异常")
    void createGoods_negativePrice_fails() throws Exception {
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest("SKU-NP", "X", new BigDecimal("-1.00"), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("创建商品 - 空编码 → 业务异常")
    void createGoods_blankCode_fails() throws Exception {
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest("", "X", new BigDecimal("10.00"), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ GET /api/goods/{id} ============================

    @Test
    @DisplayName("按 ID 查询商品 - 成功")
    void getById_returnsGoods() throws Exception {
        Long id = createGoods("SKU-GBI", "GBI Item", "50.00", 10);

        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.code").value("SKU-GBI"));
    }

    @Test
    @DisplayName("按 ID 查询商品 - 不存在 → 业务异常")
    void getById_notFound_fails() throws Exception {
        mockMvc.perform(get("/api/goods/9999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ GET /api/goods/by-code ============================

    @Test
    @DisplayName("按编码查询商品 - 成功")
    void getByCode_returnsGoods() throws Exception {
        String code = "SKU-" + UUID.randomUUID();
        createGoods(code, "CodeItem", "10.00", 5);

        mockMvc.perform(get("/api/goods/by-code").param("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code));
    }

    @Test
    @DisplayName("按编码查询商品 - 不存在 → 业务异常")
    void getByCode_notFound_fails() throws Exception {
        mockMvc.perform(get("/api/goods/by-code").param("code", "no-such-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ PUT /api/goods/{id} ============================

    @Test
    @DisplayName("更新商品 - 修改名称和价格")
    void updateGoods_changesNameAndPrice() throws Exception {
        Long id = createGoods("SKU-UPD", "Old Name", "10.00", 10);

        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("New Name", new BigDecimal("99.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("goods updated"))
                .andExpect(jsonPath("$.data.name").value("New Name"))
                .andExpect(jsonPath("$.data.price").value(99.00));
    }

    @Test
    @DisplayName("更新商品 - 不存在 → 业务异常")
    void updateGoods_notFound_fails() throws Exception {
        mockMvc.perform(put("/api/goods/9999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("X", new BigDecimal("1.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("更新商品 - 已删除不可更新")
    void updateGoods_deletedGoods_fails() throws Exception {
        Long id = createGoods("SKU-DEL-UPD", "X", "10.00", 10);
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());

        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("Y", new BigDecimal("20.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ PUT /api/goods/{id}/status ============================

    @Test
    @DisplayName("修改商品状态 - DRAFT → ON_SALE")
    void changeStatus_toOnSale_succeeds() throws Exception {
        Long id = createGoods("SKU-STATUS-1", "X", "10.00", 10);

        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("goods status changed"))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    @DisplayName("修改商品状态 - ON_SALE → OFF_SALE")
    void changeStatus_toOffSale_succeeds() throws Exception {
        Long id = createGoods("SKU-STATUS-2", "X", "10.00", 10);
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE")).andExpect(status().isOk());

        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "OFF_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));
    }

    @Test
    @DisplayName("修改商品状态 - 已删除商品禁止变更")
    void changeStatus_deletedGoods_fails() throws Exception {
        Long id = createGoods("SKU-STATUS-DEL", "X", "10.00", 10);
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());

        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ DELETE /api/goods/{id} ============================

    @Test
    @DisplayName("删除商品 - 软删成功")
    void deleteGoods_softDelete_succeeds() throws Exception {
        Long id = createGoods("SKU-DEL-1", "DeleteMe", "10.00", 10);

        mockMvc.perform(delete("/api/goods/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("goods deleted"));

        // 验证已为 DELETED 状态
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    @DisplayName("删除商品 - 不存在 → 业务异常")
    void deleteGoods_notFound_fails() throws Exception {
        mockMvc.perform(delete("/api/goods/9999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ GET /api/goods/page ============================

    @Test
    @DisplayName("分页查询 - 默认全表")
    void pageQuery_default_succeeds() throws Exception {
        mockMvc.perform(get("/api/goods/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("分页查询 - 按状态过滤")
    void pageQuery_byStatus_succeeds() throws Exception {
        // 创建两个上架商品
        Long id1 = createGoods("SKU-PQ-1", "PQ1", "10.00", 1);
        Long id2 = createGoods("SKU-PQ-2", "PQ2", "20.00", 1);
        mockMvc.perform(put("/api/goods/" + id1 + "/status").param("status", "ON_SALE")).andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id2 + "/status").param("status", "ON_SALE")).andExpect(status().isOk());

        mockMvc.perform(get("/api/goods/page").param("status", "ON_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    // ============================ GET /api/goods/list ============================

    @Test
    @DisplayName("列表查询 - 全部")
    void listQuery_default_succeeds() throws Exception {
        mockMvc.perform(get("/api/goods/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ============================ GET /api/goods/count ============================

    @Test
    @DisplayName("总数查询 - 全部")
    void countQuery_default_succeeds() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/goods/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("data").asLong()).isGreaterThanOrEqualTo(0L);
    }

    // ============================ 完整流程 ============================

    @Test
    @DisplayName("完整业务流 - 创建→更新→改状态→软删")
    void fullLifecycle_createUpdateChangeStatusDelete() throws Exception {
        Long id = createGoods("SKU-FULL", "FullItem", "100.00", 50);

        // 更新
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("Updated", new BigDecimal("200.00")))))
                .andExpect(jsonPath("$.data.name").value("Updated"));

        // 改状态
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        // 删除
        mockMvc.perform(delete("/api/goods/" + id))
                .andExpect(jsonPath("$.msg").value("goods deleted"));

        // 查询应仍可查到，但状态为 DELETED
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    @DisplayName("状态枚举值正确")
    void goodsStatusEnum_allValues() {
        assertThat(GoodsStatus.values()).containsExactly(
                GoodsStatus.ON_SALE, GoodsStatus.OFF_SALE, GoodsStatus.DRAFT, GoodsStatus.DELETED);
    }
}
