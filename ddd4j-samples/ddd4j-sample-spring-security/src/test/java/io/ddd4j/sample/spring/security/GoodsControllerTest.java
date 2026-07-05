package io.ddd4j.sample.spring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.security.goods.web.GoodsController.CreateGoodsRequest;
import io.ddd4j.sample.spring.security.goods.web.GoodsController.UpdateGoodsRequest;
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
 * Goods CRUD 测试（Spring Security 示例）。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Goods 业务 - Spring Security")
class GoodsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private Long createGoods(String code, String name, int stock, BigDecimal price) throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateGoodsRequest(code, name, price, stock));
        MvcResult r = mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Test
    @DisplayName("创建商品 - 成功")
    void create_succeeds() throws Exception {
        Long id = createGoods("GC-" + UUID.randomUUID(), "Phone", 10, new BigDecimal("2999.00"));
        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("创建商品 - 默认状态为 DRAFT")
    void create_defaultStatus() throws Exception {
        Long id = createGoods("GC-DRAFT-" + UUID.randomUUID(), "Phone", 10, new BigDecimal("2999.00"));
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("创建商品 - 编码为空 → 业务异常")
    void create_blankCode_fails() throws Exception {
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest("", "X", new BigDecimal("1.00"), 1))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("创建商品 - 价格为负 → 业务异常")
    void create_negativePrice_fails() throws Exception {
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest("GC-NEG-" + UUID.randomUUID(), "X",
                                        new BigDecimal("-1.00"), 1))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("创建商品 - 库存为负 → 业务异常")
    void create_negativeStock_fails() throws Exception {
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest("GC-NS-" + UUID.randomUUID(), "X",
                                        new BigDecimal("1.00"), -1))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("按 ID 查询 - 成功")
    void findById_succeeds() throws Exception {
        Long id = createGoods("GC-FIND-" + UUID.randomUUID(), "Item", 5, new BigDecimal("10.00"));
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("按 ID 查询 - 不存在 → 业务异常")
    void findById_notFound_fails() throws Exception {
        mockMvc.perform(get("/api/goods/9999999"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("按编码查询 - 成功")
    void findByCode_succeeds() throws Exception {
        String code = "GC-CODE-" + UUID.randomUUID();
        createGoods(code, "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(get("/api/goods/by-code").param("code", code))
                .andExpect(jsonPath("$.data.code").value(code));
    }

    @Test
    @DisplayName("按编码查询 - 不存在 → 业务异常")
    void findByCode_notFound_fails() throws Exception {
        mockMvc.perform(get("/api/goods/by-code").param("code", "no-such-code"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("更新商品 - 名称")
    void update_name() throws Exception {
        Long id = createGoods("GC-U-" + UUID.randomUUID(), "Old", 1, new BigDecimal("10.00"));
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("NewName", null))))
                .andExpect(jsonPath("$.data.name").value("NewName"));
    }

    @Test
    @DisplayName("更新商品 - 价格")
    void update_price() throws Exception {
        Long id = createGoods("GC-UP-" + UUID.randomUUID(), "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest(null, new BigDecimal("999.00")))))
                .andExpect(jsonPath("$.data.price").value("999.00"));
    }

    @Test
    @DisplayName("更新商品 - 不存在 → 业务异常")
    void update_notFound_fails() throws Exception {
        mockMvc.perform(put("/api/goods/9999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("X", null))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("更新商品 - 已删除 → 业务异常")
    void update_deleted_fails() throws Exception {
        Long id = createGoods("GC-DEL-" + UUID.randomUUID(), "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("Y", null))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("删除商品 - 成功")
    void delete_succeeds() throws Exception {
        Long id = createGoods("GC-D-" + UUID.randomUUID(), "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    @DisplayName("删除商品 - 不存在 → 业务异常")
    void delete_notFound_fails() throws Exception {
        mockMvc.perform(delete("/api/goods/9999999")).andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("修改状态 - DRAFT → ON_SALE")
    void updateStatus_toOnSale() throws Exception {
        Long id = createGoods("GC-S-" + UUID.randomUUID(), "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    @DisplayName("修改状态 - ON_SALE → OFF_SALE")
    void updateStatus_toOffSale() throws Exception {
        Long id = createGoods("GC-OS-" + UUID.randomUUID(), "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "OFF_SALE"))
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));
    }

    @Test
    @DisplayName("修改状态 - 已删除 → 业务异常")
    void updateStatus_deleted_fails() throws Exception {
        Long id = createGoods("GC-DS-" + UUID.randomUUID(), "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("列表查询 - 返回数组")
    void list_returns() throws Exception {
        createGoods("GC-L1-" + UUID.randomUUID(), "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(get("/api/goods/list"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("分页查询 - 返回分页对象")
    void page_returns() throws Exception {
        mockMvc.perform(get("/api/goods/page"))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("计数查询 - 返回数字")
    void count_returns() throws Exception {
        mockMvc.perform(get("/api/goods/count"))
                .andExpect(jsonPath("$.data").isNumber());
    }
}