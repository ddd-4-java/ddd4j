package io.ddd4j.sample.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.order.domain.model.OrderStatus;
import io.ddd4j.sample.spring.order.web.dto.AddOrderLineRequest;
import io.ddd4j.sample.spring.order.web.dto.CreateOrderRequest;
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
 * 订单 REST 写侧集成测试（{@code POST /orders*}）。
 *
 * <p>使用 MockMvc 完整驱动控制器，覆盖订单写侧的 6 个端点 + 1 个预览：
 * 创建 → 添加订单行 → 支付 → 发货 → 取消 → 折扣预览，并验证状态机不变量。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Order REST 写侧集成测试")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 创建一个草稿订单并返回其订单 ID。
     */
    private String createDraft(String orderNo, String buyerId, String buyerName) throws Exception {
        MvcResult result = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(orderNo, buyerId, buyerName))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asText();
    }

    /**
     * 为订单添加一个订单行。
     */
    private void addLine(String orderId, String goodsId, String goodsName, int qty, BigDecimal price) throws Exception {
        mockMvc.perform(post("/orders/" + orderId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AddOrderLineRequest(goodsId, goodsName, qty, price))))
                .andExpect(status().isOk());
    }

    // ============================ POST /orders ============================

    @Test
    @DisplayName("创建订单 - 成功")
    void createOrder_returnsDraftOrder() throws Exception {
        String orderNo = "ORD-" + UUID.randomUUID();
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(orderNo, "BUYER-1", "Alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("order created"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.buyerId").value("BUYER-1"))
                .andExpect(jsonPath("$.data.buyerName").value("Alice"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.currency").value("CNY"));
    }

    @Test
    @DisplayName("创建订单 - 业务码 OK 等于 0")
    void createOrder_responseCodeIsZero() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest("ORD-CODE", "B-2", "Bob"))))
                .andExpect(jsonPath("$.code").value(0));
    }

    // ============================ GET /orders/{id} ============================

    @Test
    @DisplayName("按 ID 查询订单 - 成功")
    void findById_returnsOrder() throws Exception {
        String id = createDraft("ORD-FIND-1", "B-FIND-1", "Finder");

        mockMvc.perform(get("/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-FIND-1"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("按 ID 查询订单 - 不存在抛 IllegalArgumentException")
    void findById_notFound_throws() throws Exception {
        mockMvc.perform(get("/orders/not-existed-id-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ POST /orders/{id}/lines ============================

    @Test
    @DisplayName("添加订单行 - 成功")
    void addLine_succeeds() throws Exception {
        String id = createDraft("ORD-LINE-1", "B-LINE-1", "LineBuyer");

        mockMvc.perform(post("/orders/" + id + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddOrderLineRequest("SKU-A", "商品 A", 2, new BigDecimal("30.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("order line added"))
                .andExpect(jsonPath("$.data.lines[0].goodsId").value("SKU-A"))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(2));
    }

    @Test
    @DisplayName("添加订单行 - 订单不存在")
    void addLine_orderNotFound() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddOrderLineRequest("SKU-X", "X", 1, new BigDecimal("10.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("添加多个订单行 - 总金额累加")
    void addMultipleLines_totalAmountAccumulated() throws Exception {
        String id = createDraft("ORD-LINE-MULTI", "B-MULTI", "MultiBuyer");
        addLine(id, "SKU-1", "Item1", 1, new BigDecimal("50.00"));
        addLine(id, "SKU-2", "Item2", 2, new BigDecimal("25.00"));

        MvcResult result = mockMvc.perform(get("/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("data").path("totalAmount").asText()).isEqualTo("100.00");
    }

    // ============================ POST /orders/{id}/pay ============================

    @Test
    @DisplayName("支付订单 - 草稿 + 含订单行 → 已支付")
    void pay_draftWithLine_succeeds() throws Exception {
        String id = createDraft("ORD-PAY-1", "B-PAY-1", "Payer");
        addLine(id, "SKU-1", "Item", 1, new BigDecimal("20.00"));

        mockMvc.perform(post("/orders/" + id + "/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("order paid"))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @DisplayName("支付订单 - 空订单行 → 业务异常（code 1）")
    void pay_emptyOrder_fails() throws Exception {
        String id = createDraft("ORD-PAY-EMPTY", "B-PAY", "Payer");

        mockMvc.perform(post("/orders/" + id + "/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("支付不存在的订单 → 业务异常")
    void pay_orderNotFound_fails() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("重复支付已支付订单 → 业务异常")
    void pay_alreadyPaidOrder_fails() throws Exception {
        String id = createDraft("ORD-PAY-TWICE", "B-PT", "Payer");
        addLine(id, "SKU-1", "Item", 1, new BigDecimal("10.00"));
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());

        // 第二次支付
        mockMvc.perform(post("/orders/" + id + "/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ POST /orders/{id}/ship ============================

    @Test
    @DisplayName("发货 - 仅已支付订单可发货")
    void ship_paidOrder_succeeds() throws Exception {
        String id = createDraft("ORD-SHIP-1", "B-SHIP", "Shipper");
        addLine(id, "SKU-1", "Item", 1, new BigDecimal("15.00"));
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());

        mockMvc.perform(post("/orders/" + id + "/ship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("order shipped"))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("发货 - 未支付订单 → 业务异常")
    void ship_draftOrder_fails() throws Exception {
        String id = createDraft("ORD-SHIP-DRAFT", "B-SHIP", "Shipper");
        addLine(id, "SKU-1", "Item", 1, new BigDecimal("15.00"));

        mockMvc.perform(post("/orders/" + id + "/ship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("发货 - 不存在的订单 → 业务异常")
    void ship_orderNotFound_fails() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/ship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ POST /orders/{id}/cancel ============================

    @Test
    @DisplayName("取消订单 - 草稿可取消")
    void cancel_draftOrder_succeeds() throws Exception {
        String id = createDraft("ORD-CANCEL-DRAFT", "B-CANCEL", "Canceller");

        mockMvc.perform(post("/orders/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("order cancelled"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("取消订单 - 已支付订单可取消")
    void cancel_paidOrder_succeeds() throws Exception {
        String id = createDraft("ORD-CANCEL-PAID", "B-CANCEL", "Canceller");
        addLine(id, "SKU-1", "Item", 1, new BigDecimal("10.00"));
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());

        mockMvc.perform(post("/orders/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("取消订单 - 已发货 → 业务异常")
    void cancel_shippedOrder_fails() throws Exception {
        String id = createDraft("ORD-CANCEL-SHIPPED", "B-CANCEL", "Canceller");
        addLine(id, "SKU-1", "Item", 1, new BigDecimal("10.00"));
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/ship")).andExpect(status().isOk());

        mockMvc.perform(post("/orders/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("取消不存在的订单 → 业务异常")
    void cancel_orderNotFound_fails() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ============================ GET /orders/{id}/discount ============================

    @Test
    @DisplayName("预览折扣 - 小于 500 元不打折")
    void previewDiscount_lowAmount_noDiscount() throws Exception {
        String id = createDraft("ORD-DISC-LOW", "B-DISC", "Buyer");
        addLine(id, "SKU-1", "Item", 1, new BigDecimal("100.00"));

        mockMvc.perform(get("/orders/" + id + "/discount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value("100.00"))
                .andExpect(jsonPath("$.data.currency").value("CNY"));
    }

    @Test
    @DisplayName("预览折扣 - 高于 500 元 95 折")
    void previewDiscount_highAmount_95Percent() throws Exception {
        String id = createDraft("ORD-DISC-HIGH", "B-DISC", "Buyer");
        addLine(id, "SKU-1", "Item", 1, new BigDecimal("600.00"));

        mockMvc.perform(get("/orders/" + id + "/discount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value("570.00"));
    }

    // ============================ 端到端流程 ============================

    @Test
    @DisplayName("完整业务流 - 创建→添加订单行→支付→发货→查询")
    void fullLifecycle_createAddPayShipQuery() throws Exception {
        String id = createDraft("ORD-FULL-1", "B-FULL", "FullBuyer");
        addLine(id, "SKU-1", "Item", 3, new BigDecimal("100.00"));
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/ship")).andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("data").path("totalAmount").asText()).isEqualTo("300.00");
    }

    @Test
    @DisplayName("状态枚举值正确：DRAFT/PAID/SHIPPED/CANCELLED")
    void orderStatusEnum_allValues() {
        assertThat(OrderStatus.values()).containsExactly(
                OrderStatus.DRAFT, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.CANCELLED);
    }
}
