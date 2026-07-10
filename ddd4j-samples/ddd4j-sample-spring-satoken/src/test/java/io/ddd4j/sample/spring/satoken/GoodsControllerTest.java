package io.ddd4j.sample.spring.satoken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.satoken.goods.web.GoodsController.CreateGoodsRequest;
import io.ddd4j.sample.spring.satoken.goods.web.GoodsController.UpdateGoodsRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Goods 业务流测试（Sa-Token 示例内的业务部分）。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Goods 业务流 - Sa-Token")
class GoodsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Long createGoods(String code, String name, String price, int stock) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, name, new BigDecimal(price), stock))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    // ============================ POST ============================

    @Test
    @DisplayName("创建商品 - 成功")
    void createGoods_succeeds() throws Exception {
        String code = "SKU-" + UUID.randomUUID();
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, "TestItem", new BigDecimal("99.00"), 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("创建商品 - 编码重复 → 业务异常")
    void createGoods_duplicateCode_fails() throws Exception {
        String code = "SKU-DUP-" + UUID.randomUUID();
        createGoods(code, "First", "10.00", 1);
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, "Dup", new BigDecimal("20.00"), 1))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("创建商品 - 负库存 → 业务异常")
    void createGoods_negativeStock_fails() throws Exception {
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest("SKU-NEG", "X", new BigDecimal("1.00"), -1))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("创建商品 - 空编码 → 业务异常")
    void createGoods_blankCode_fails() throws Exception {
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest("", "X", new BigDecimal("1.00"), 1))))
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ GET /api/goods/{id} ============================

    @Test
    @DisplayName("按 ID 查询")
    void getById_succeeds() throws Exception {
        Long id = createGoods("SKU-GBI-" + UUID.randomUUID(), "X", "1.00", 1);
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("按 ID 查询 - 不存在 → 业务异常")
    void getById_notFound_fails() throws Exception {
        mockMvc.perform(get("/api/goods/99999999"))
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ GET /api/goods/by-code ============================

    @Test
    @DisplayName("按 code 查询")
    void getByCode_succeeds() throws Exception {
        String code = "SKU-BC-" + UUID.randomUUID();
        createGoods(code, "X", "1.00", 1);
        mockMvc.perform(get("/api/goods/by-code").param("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code));
    }

    // ============================ PUT ============================

    @Test
    @DisplayName("更新商品")
    void update_succeeds() throws Exception {
        Long id = createGoods("SKU-UPD-" + UUID.randomUUID(), "Old", "10.00", 10);
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("New", new BigDecimal("99.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New"))
                .andExpect(jsonPath("$.data.price").value(99.00));
    }

    @Test
    @DisplayName("更新商品 - 不存在 → 业务异常")
    void update_notFound_fails() throws Exception {
        mockMvc.perform(put("/api/goods/9999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("X", new BigDecimal("1.00")))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("更新商品 - 已删除不可更新")
    void update_deleted_fails() throws Exception {
        Long id = createGoods("SKU-DEL-U-" + UUID.randomUUID(), "X", "10.00", 1);
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("Y", new BigDecimal("20.00")))))
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ PUT /api/goods/{id}/status ============================

    @Test
    @DisplayName("改状态 - DRAFT → ON_SALE")
    void changeStatus_toOnSale() throws Exception {
        Long id = createGoods("SKU-ST-" + UUID.randomUUID(), "X", "10.00", 1);
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    @DisplayName("改状态 - ON_SALE → OFF_SALE")
    void changeStatus_toOffSale() throws Exception {
        Long id = createGoods("SKU-ST-2-" + UUID.randomUUID(), "X", "10.00", 1);
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE")).andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "OFF_SALE"))
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));
    }

    @Test
    @DisplayName("改状态 - 已删除商品 → 业务异常")
    void changeStatus_deleted_fails() throws Exception {
        Long id = createGoods("SKU-ST-D-" + UUID.randomUUID(), "X", "10.00", 1);
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ DELETE ============================

    @Test
    @DisplayName("删除商品 - 软删")
    void delete_succeeds() throws Exception {
        Long id = createGoods("SKU-D-" + UUID.randomUUID(), "X", "10.00", 1);
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    @DisplayName("删除商品 - 不存在 → 业务异常")
    void delete_notFound_fails() throws Exception {
        mockMvc.perform(delete("/api/goods/99999999"))
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ GET /api/goods/page ============================

    @Test
    @DisplayName("分页查询 - 默认")
    void pageQuery_default() throws Exception {
        mockMvc.perform(get("/api/goods/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("分页查询 - 按状态过滤")
    void pageQuery_byStatus() throws Exception {
        Long id = createGoods("SKU-PQ-" + UUID.randomUUID(), "X", "10.00", 1);
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE")).andExpect(status().isOk());
        mockMvc.perform(get("/api/goods/page").param("status", "ON_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    // ============================ GET /api/goods/list ============================

    @Test
    @DisplayName("列表查询")
    void listQuery_succeeds() throws Exception {
        mockMvc.perform(get("/api/goods/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ============================ GET /api/goods/count ============================

    @Test
    @DisplayName("数量查询")
    void countQuery_succeeds() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/goods/count"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.path("data").asLong()).isGreaterThanOrEqualTo(0L);
    }

    // ============================ 完整流程 ============================

    @Test
    @DisplayName("完整商品流 - 创建→更新→改状态→软删")
    void fullLifecycle_createUpdateStatusDelete() throws Exception {
        Long id = createGoods("SKU-FULL", "FullItem", "100.00", 50);
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("Updated", new BigDecimal("200.00")))))
                .andExpect(jsonPath("$.data.name").value("Updated"));
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }
}
