package io.ddd4j.sample.spring.shiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.shiro.order.web.dto.AddOrderLineRequest;
import io.ddd4j.sample.spring.shiro.order.web.dto.CreateOrderRequest;
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
 * Order 业务流测试（Shiro 示例内的业务部分）。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Order 业务流 - Shiro")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String createOrder(String orderNo) throws Exception {
        MvcResult r = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(orderNo, "BUYER-1", "Alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private void addLine(String orderId, BigDecimal price, int qty) throws Exception {
        mockMvc.perform(post("/orders/" + orderId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddOrderLineRequest("SKU-1", "Item", qty, price))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("创建订单 - 成功")
    void createOrder_succeeds() throws Exception {
        String orderNo = "ORD-" + UUID.randomUUID();
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(orderNo, "B-1", "Alice"))))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo));
    }

    @Test
    @DisplayName("创建订单 - 返回订单 ID")
    void createOrder_returnsId() throws Exception {
        assertThat(createOrder("ORD-ID-1")).isNotEmpty();
    }

    @Test
    @DisplayName("按 ID 查询订单 - 成功")
    void findById_returns() throws Exception {
        String id = createOrder("ORD-FIND-1");
        mockMvc.perform(get("/orders/" + id))
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("按 ID 查询订单 - 不存在 → 业务异常")
    void findById_notFound() throws Exception {
        mockMvc.perform(get("/orders/no-such-order"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("按订单号查询 - 成功")
    void findByOrderNo_returns() throws Exception {
        String orderNo = "ORD-BY-NO-" + UUID.randomUUID();
        createOrder(orderNo);
        mockMvc.perform(get("/orders/by-order-no").param("orderNo", orderNo))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo));
    }

    @Test
    @DisplayName("列表查询 - 返回数组")
    void listAll_succeeds() throws Exception {
        createOrder("ORD-LIST-1");
        mockMvc.perform(get("/orders"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("添加订单行")
    void addLine_succeeds() throws Exception {
        String id = createOrder("ORD-LINE");
        addLine(id, new BigDecimal("25.00"), 2);
        mockMvc.perform(get("/orders/" + id))
                .andExpect(jsonPath("$.data.lines[0].goodsId").value("SKU-1"))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(2));
    }

    @Test
    @DisplayName("添加订单行 - 不存在 → 业务异常")
    void addLine_notFound() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddOrderLineRequest("X", "X", 1, new BigDecimal("10.00")))))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("支付订单 - 草稿+订单行 → PAID")
    void pay_succeeds() throws Exception {
        String id = createOrder("ORD-PAY-1");
        addLine(id, new BigDecimal("30.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/pay"))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @DisplayName("支付订单 - 空订单行 → 业务异常")
    void pay_emptyOrder_fails() throws Exception {
        String id = createOrder("ORD-PAY-EMPTY");
        mockMvc.perform(post("/orders/" + id + "/pay"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("支付订单 - 不存在 → 业务异常")
    void pay_notFound_fails() throws Exception {
        mockMvc.perform(post("/orders/no-such-order/pay"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("发货 - 仅 PAID → SHIPPED")
    void ship_paidOrder_succeeds() throws Exception {
        String id = createOrder("ORD-SHIP-1");
        addLine(id, new BigDecimal("15.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/ship"))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("发货 - 草稿订单 → 业务异常")
    void ship_draft_fails() throws Exception {
        String id = createOrder("ORD-SHIP-D");
        addLine(id, new BigDecimal("10.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/ship"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("取消订单 - 草稿 → CANCELLED")
    void cancel_draft_succeeds() throws Exception {
        String id = createOrder("ORD-CANCEL-D");
        mockMvc.perform(post("/orders/" + id + "/cancel"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("取消订单 - PAID → CANCELLED")
    void cancel_paid_succeeds() throws Exception {
        String id = createOrder("ORD-CANCEL-P");
        addLine(id, new BigDecimal("10.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/cancel"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("取消订单 - SHIPPED → 业务异常")
    void cancel_shipped_fails() throws Exception {
        String id = createOrder("ORD-CANCEL-S");
        addLine(id, new BigDecimal("10.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/ship")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/cancel"))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("折扣预览 - 大于 500 元 95 折")
    void previewDiscount_highAmount() throws Exception {
        String id = createOrder("ORD-DISC");
        addLine(id, new BigDecimal("600.00"), 1);
        mockMvc.perform(get("/orders/" + id + "/discount"))
                .andExpect(jsonPath("$.data.amount").value("570.00"));
    }

    @Test
    @DisplayName("完整订单流")
    void fullLifecycle() throws Exception {
        String id = createOrder("ORD-FULL-LIFECYCLE");
        addLine(id, new BigDecimal("100.00"), 3);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/ship")).andExpect(status().isOk());
        mockMvc.perform(get("/orders/" + id))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("重复支付 → 业务异常")
    void payTwice_fails() throws Exception {
        String id = createOrder("ORD-PAY-TWICE");
        addLine(id, new BigDecimal("10.00"), 1);
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/pay")).andExpect(jsonPath("$.code").value(1));
    }
}
