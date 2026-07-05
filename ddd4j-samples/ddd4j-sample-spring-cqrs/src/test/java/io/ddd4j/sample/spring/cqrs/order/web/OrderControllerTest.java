package io.ddd4j.sample.spring.cqrs.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.cqrs.SpringCqrsApplication;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Order 充血模型 REST 端点测试（CQRS 写侧）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>CRUD：创建 / 查询（按 ID、订单号）/ 列表</li>
 *   <li>状态机：draft → addLine → pay → ship → cancel 全流程</li>
 *   <li>不变式：空订单行不能支付、重复支付失败、已发货不能取消</li>
 *   <li>领域服务：discount 预览</li>
 * </ul>
 *
 * @author Test
 */
@SpringBootTest(classes = SpringCqrsApplication.class)
@AutoConfigureMockMvc
@DisplayName("Order REST 写侧 - 充血模型状态机")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 辅助方法：创建一个草稿订单，返回订单 ID。
     */
    private String createOrder(String buyerId) throws Exception {
        String orderNo = "O-" + UUID.randomUUID();
        MvcResult r = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(orderNo, buyerId, "Alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    /**
     * 辅助方法：给订单增加一个订单行。
     */
    private void addLine(String orderId, BigDecimal price, int qty) throws Exception {
        mockMvc.perform(post("/orders/" + orderId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddOrderLineRequest("SKU-" + UUID.randomUUID(),
                                        "iPhone 15", qty, price))))
                .andExpect(status().isOk());
    }

    // =========================== CRUD ===========================

    @Test
    @DisplayName("创建订单 - 成功，状态为 DRAFT")
    void createOrder_succeeds_withDraftStatus() throws Exception {
        String orderNo = "ORD-CREATE-" + UUID.randomUUID();
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(orderNo, "B-1", "Alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("创建订单 - 返回非空订单 ID")
    void createOrder_returnsNonEmptyId() throws Exception {
        String id = createOrder("B-ID-" + UUID.randomUUID());
        assertThat(id).isNotBlank();
    }

    @Test
    @DisplayName("按 ID 查询订单 - 成功")
    void findById_succeeds() throws Exception {
        String id = createOrder("B-FIND-" + UUID.randomUUID());
        mockMvc.perform(get("/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("按 ID 查询订单 - 不存在 → 业务异常")
    void findById_notFound_returnsError() throws Exception {
        mockMvc.perform(get("/orders/no-such-order-id"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("按订单编号查询 - 成功")
    void findByOrderNo_succeeds() throws Exception {
        String orderNo = "ORD-NO-" + UUID.randomUUID();
        String id = createOrder("B-NO-" + UUID.randomUUID());
        // 重置订单号为已知值便于查询不太现实：使用自定义订单号创建
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(orderNo, "B-NO-Q", "Bob"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/orders/by-no").param("orderNo", orderNo))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.id").exists());
        // 避免 id 未使用告警
        assertThat(id).isNotBlank();
    }

    @Test
    @DisplayName("按订单编号查询 - 不存在 → 业务异常")
    void findByOrderNo_notFound_returnsError() throws Exception {
        mockMvc.perform(get("/orders/by-no").param("orderNo", "NO-NOT-FOUND"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("列表查询 - 返回分页结构")
    void listAll_succeeds() throws Exception {
        createOrder("B-LIST-" + UUID.randomUUID());
        mockMvc.perform(get("/orders").param("page", "1").param("pageSize", "10"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").value(1));
    }

    // =========================== 订单行 ===========================

    @Test
    @DisplayName("添加订单行 - 成功，数据回填到聚合")
    void addLine_succeeds_andPersists() throws Exception {
        String id = createOrder("B-LINE-" + UUID.randomUUID());
        addLine(id, new BigDecimal("25.00"), 3);

        mockMvc.perform(get("/orders/" + id))
                .andExpect(jsonPath("$.data.lines.length()").value(1))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value("25.00"))
                .andExpect(jsonPath("$.data.totalAmount").value("75.00"));
    }

    @Test
    @DisplayName("添加订单行 - 订单不存在 → 业务异常")
    void addLine_notFound_fails() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddOrderLineRequest("X", "X", 1, new BigDecimal("10.00")))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("添加多条订单行 - 总金额累加")
    void addMultipleLines_totalSummed() throws Exception {
        String id = createOrder("B-MULTI-" + UUID.randomUUID());
        addLine(id, new BigDecimal("10.00"), 2);
        addLine(id, new BigDecimal("5.00"), 4);
        mockMvc.perform(get("/orders/" + id))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value("40.00"));
    }

    // =========================== 状态机：pay ===========================

    @Test
    @DisplayName("支付 - 草稿+订单行 → PAID")
    void pay_withLines_succeeds() throws Exception {
        String id = createOrder("B-PAY-" + UUID.randomUUID());
        addLine(id, new BigDecimal("100.00"), 1);

        mockMvc.perform(post("/orders/" + id + "/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @DisplayName("支付 - 空订单行 → 业务异常")
    void pay_emptyOrder_fails() throws Exception {
        String id = createOrder("B-PAY-EMPTY-" + UUID.randomUUID());
        mockMvc.perform(post("/orders/" + id + "/pay"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("支付 - 订单不存在 → 业务异常")
    void pay_notFound_fails() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/pay"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("支付 - 重复支付 → 业务异常（状态机守门）")
    void payTwice_fails() throws Exception {
        String id = createOrder("B-PAY-TWICE-" + UUID.randomUUID());
        addLine(id, new BigDecimal("10.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/pay"))
                .andExpect(jsonPath("$.code").value(1));
    }

    // =========================== 状态机：ship ===========================

    @Test
    @DisplayName("发货 - 仅 PAID → SHIPPED")
    void ship_paidOrder_succeeds() throws Exception {
        String id = createOrder("B-SHIP-" + UUID.randomUUID());
        addLine(id, new BigDecimal("15.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());

        mockMvc.perform(post("/orders/" + id + "/ship"))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("发货 - 草稿订单 → 业务异常")
    void ship_draft_fails() throws Exception {
        String id = createOrder("B-SHIP-D-" + UUID.randomUUID());
        addLine(id, new BigDecimal("10.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/ship"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("发货 - 订单不存在 → 业务异常")
    void ship_notFound_fails() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/ship"))
                .andExpect(jsonPath("$.code").value(1));
    }

    // =========================== 状态机：cancel ===========================

    @Test
    @DisplayName("取消 - 草稿 → CANCELLED")
    void cancel_draft_succeeds() throws Exception {
        String id = createOrder("B-CANCEL-D-" + UUID.randomUUID());
        mockMvc.perform(post("/orders/" + id + "/cancel"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("取消 - PAID → CANCELLED")
    void cancel_paid_succeeds() throws Exception {
        String id = createOrder("B-CANCEL-P-" + UUID.randomUUID());
        addLine(id, new BigDecimal("10.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/cancel"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("取消 - SHIPPED → 业务异常")
    void cancel_shipped_fails() throws Exception {
        String id = createOrder("B-CANCEL-S-" + UUID.randomUUID());
        addLine(id, new BigDecimal("10.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/ship")).andExpect(status().isOk());

        mockMvc.perform(post("/orders/" + id + "/cancel"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("取消 - 订单不存在 → 业务异常")
    void cancel_notFound_fails() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/cancel"))
                .andExpect(jsonPath("$.code").value(1));
    }

    // =========================== 领域服务：discount ===========================

    @Test
    @DisplayName("折扣预览 - 大于等于 1000 元 9 折")
    void previewDiscount_highAmount_returns90Percent() throws Exception {
        String id = createOrder("B-DISC-1K-" + UUID.randomUUID());
        // 1000 * 0.9 = 900（折扣 10% 由 OrderDomainService 实现）
        addLine(id, new BigDecimal("1000.00"), 1);

        mockMvc.perform(get("/orders/" + id + "/discount"))
                .andExpect(jsonPath("$.data.amount").value("900.00"))
                .andExpect(jsonPath("$.data.currency").value("CNY"));
    }

    @Test
    @DisplayName("折扣预览 - 500~1000 元 95 折")
    void previewDiscount_midAmount_returns95Percent() throws Exception {
        String id = createOrder("B-DISC-500-" + UUID.randomUUID());
        // 600 * 0.95 = 570
        addLine(id, new BigDecimal("600.00"), 1);

        mockMvc.perform(get("/orders/" + id + "/discount"))
                .andExpect(jsonPath("$.data.amount").value("570.00"));
    }

    // =========================== 完整生命周期 ===========================

    @Test
    @DisplayName("完整业务流：draft → addLine → pay → ship")
    void fullLifecycle_draftToShipped() throws Exception {
        String id = createOrder("B-FULL-" + UUID.randomUUID());
        addLine(id, new BigDecimal("100.00"), 2);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/ship")).andExpect(status().isOk());

        mockMvc.perform(get("/orders/" + id))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.totalAmount").value("200.00"));
    }

    @Test
    @DisplayName("草稿订单被取消后仍可查询到（终态）")
    void cancelThenRead_stillReadable() throws Exception {
        String id = createOrder("B-READ-" + UUID.randomUUID());
        mockMvc.perform(post("/orders/" + id + "/cancel")).andExpect(status().isOk());

        mockMvc.perform(get("/orders/" + id))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
