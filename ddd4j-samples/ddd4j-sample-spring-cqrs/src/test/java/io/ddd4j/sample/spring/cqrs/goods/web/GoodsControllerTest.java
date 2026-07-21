package io.ddd4j.sample.spring.cqrs.goods.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.cqrs.SpringCqrsApplication;
import io.ddd4j.sample.spring.cqrs.goods.web.dto.CreateGoodsRequest;
import io.ddd4j.sample.spring.cqrs.goods.web.dto.UpdateGoodsRequest;
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
 * Goods 第三轨 REST 测试。
 *
 * <p>Goods 第三轨采用 Model/Query 快速 CRUD 模式，本测试覆盖：
 * <ul>
 *   <li>CRUD：创建 / 查询（按 ID、Code）/ 更新 / 软删除</li>
 *   <li>业务校验：空白编码、负价格、负库存</li>
 *   <li>充血查询：page / list / count</li>
 *   <li>业务唯一键：code 重复 → 业务异常</li>
 * </ul>
 *
 * @author Test
 */
@SpringBootTest(classes = SpringCqrsApplication.class)
@AutoConfigureMockMvc
@DisplayName("Goods REST - 第三轨 CRUD")
class GoodsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Long createGoods(String codeSuffix, String name, int stock, BigDecimal price) throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateGoodsRequest("SKU-" + codeSuffix + "-" + UUID.randomUUID(),
                        name, price, stock));
        MvcResult r = mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    // =========================== CRUD ===========================

    @Test
    @DisplayName("创建商品 - 成功，返回正整数 ID")
    void create_succeeds() throws Exception {
        Long id = createGoods("CR", "Phone", 10, new BigDecimal("2999.00"));
        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("创建商品 - 默认状态为 DRAFT")
    void create_defaultStatusIsDraft() throws Exception {
        Long id = createGoods("DR", "Phone-DR", 5, new BigDecimal("100.00"));
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
                                new CreateGoodsRequest("NEG-" + UUID.randomUUID(), "X",
                                        new BigDecimal("-1.00"), 1))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("创建商品 - 库存为负 → 业务异常")
    void create_negativeStock_fails() throws Exception {
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest("NS-" + UUID.randomUUID(), "X",
                                        new BigDecimal("1.00"), -1))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("创建商品 - 编码重复 → 业务异常")
    void create_duplicateCode_fails() throws Exception {
        String code = "DUP-" + UUID.randomUUID();
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, "First",
                                        new BigDecimal("10.00"), 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, "Second",
                                        new BigDecimal("20.00"), 1))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("按 ID 查询 - 成功")
    void findById_succeeds() throws Exception {
        Long id = createGoods("FID", "Item", 5, new BigDecimal("10.00"));
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
        String code = "FC-" + UUID.randomUUID();
        createGoods("FCH-" + UUID.randomUUID(), "Item", 1, new BigDecimal("10.00"));
        // 再创建一个已知 code
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, "FC", new BigDecimal("20.00"), 2))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/goods/by-code").param("code", code))
                .andExpect(jsonPath("$.data.code").value(code));
    }

    @Test
    @DisplayName("按编码查询 - 不存在 → 业务异常")
    void findByCode_notFound_fails() throws Exception {
        mockMvc.perform(get("/api/goods/by-code").param("code", "no-such-code"))
                .andExpect(jsonPath("$.code").value(1));
    }

    // =========================== 更新 / 删除 / 状态 ===========================

    @Test
    @DisplayName("更新商品 - 名称")
    void update_name() throws Exception {
        Long id = createGoods("UN", "Old", 1, new BigDecimal("10.00"));
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("NewName", null))))
                .andExpect(jsonPath("$.data.name").value("NewName"));
    }

    @Test
    @DisplayName("更新商品 - 价格")
    void update_price() throws Exception {
        Long id = createGoods("UP", "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest(null, new BigDecimal("999.00")))))
                .andExpect(jsonPath("$.data.price").value(999.0));
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
    void update_afterDelete_fails() throws Exception {
        Long id = createGoods("DEL", "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoodsRequest("Y", null))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("删除商品 - 软删除，状态置为 DELETED")
    void delete_softDeleteSetsStatus() throws Exception {
        Long id = createGoods("D", "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/goods/" + id))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    @DisplayName("删除商品 - 不存在 → 业务异常")
    void delete_notFound_fails() throws Exception {
        mockMvc.perform(delete("/api/goods/9999999"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("修改状态 - DRAFT → ON_SALE")
    void changeStatus_toOnSale() throws Exception {
        Long id = createGoods("S1", "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    @DisplayName("修改状态 - ON_SALE → OFF_SALE")
    void changeStatus_toOffSale() throws Exception {
        Long id = createGoods("S2", "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "OFF_SALE"))
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));
    }

    @Test
    @DisplayName("修改状态 - 已删除商品 → 业务异常")
    void changeStatus_afterDelete_fails() throws Exception {
        Long id = createGoods("SD", "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(delete("/api/goods/" + id)).andExpect(status().isOk());
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.code").value(1));
    }

    // =========================== 充血查询 ===========================

    @Test
    @DisplayName("充血分页 - 返回 Page 对象")
    void page_returnsPage() throws Exception {
        createGoods("P1", "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(get("/api/goods/page"))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("充血分页 - 按状态过滤")
    void page_byStatus() throws Exception {
        Long id = createGoods("P2", "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(put("/api/goods/" + id + "/status").param("status", "ON_SALE"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/goods/page").param("status", "ON_SALE"))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("充血列表 - 返回数组")
    void list_returns() throws Exception {
        createGoods("L1", "X", 1, new BigDecimal("10.00"));
        mockMvc.perform(get("/api/goods/list"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("充血列表 - 按 code 精确过滤")
    void list_byCode() throws Exception {
        String code = "LBC-" + UUID.randomUUID();
        mockMvc.perform(post("/api/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoodsRequest(code, "X",
                                        new BigDecimal("10.00"), 1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/goods/list").param("code", code))
                .andExpect(jsonPath("$.data[0].code").value(code));
    }

    @Test
    @DisplayName("充血计数 - 返回数字")
    void count_returns() throws Exception {
        mockMvc.perform(get("/api/goods/count"))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @DisplayName("充血计数 - 至少有一条记录")
    void count_atLeastOne() throws Exception {
        createGoods("C1", "X", 1, new BigDecimal("10.00"));
        MvcResult r = mockMvc.perform(get("/api/goods/count"))
                .andExpect(status().isOk())
                .andReturn();
        long cnt = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").asLong();
        assertThat(cnt).isGreaterThanOrEqualTo(1L);
    }
}
