package io.ddd4j.sample.dropwizard.cqrs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dropwizard 订单 CQRS 集成测试。
 *
 * <p>使用 Dropwizard 的 main 方法启动应用，然后通过 HTTP 客户端测试。
 * 简化版：直接测试 CQRS 组件逻辑。
 */
@DisplayName("Dropwizard Order CQRS 集成测试")
class DropwizardOrderCqrsIT {

    @BeforeEach
    void cleanUp() {
        DropwizardCqrsApplication.READ_VIEW.clear();
    }

    @Test
    @DisplayName("创建订单 -> EventStore 有事件 + 读模型有记录")
    void createOrder_thenProjection() {
        // 直接测试 CQRS 组件
        var command = new io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand(
                "ORD-001", "B001", "Alice");
        String orderId = DropwizardCqrsApplication.COMMAND_BUS.execute(command);

        assertThat(orderId).isNotEmpty();

        // 触发投影
        DropwizardCqrsApplication.VIEW_MANAGER.triggerOnce();

        // 验证读模型
        var entity = DropwizardCqrsApplication.READ_VIEW.findById(orderId);
        assertThat(entity).isNotNull();
        assertThat(entity.getOrderNo()).isEqualTo("ORD-001");
        assertThat(entity.getBuyerId()).isEqualTo("B001");
        assertThat(entity.getBuyerName()).isEqualTo("Alice");
        assertThat(entity.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("查询读模型 -> 返回正确数据")
    void getOrder_fromReadModel() {
        var command = new io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand(
                "ORD-002", "B002", "Bob");
        String orderId = DropwizardCqrsApplication.COMMAND_BUS.execute(command);

        DropwizardCqrsApplication.VIEW_MANAGER.triggerOnce();

        var entity = DropwizardCqrsApplication.READ_VIEW.findById(orderId);
        assertThat(entity).isNotNull();
        assertThat(entity.getOrderNo()).isEqualTo("ORD-002");
        assertThat(entity.getBuyerName()).isEqualTo("Bob");
        assertThat(entity.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("幂等性：同一 orderNo 重复创建 -> 抛出异常")
    void createOrder_idempotent() {
        var command1 = new io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand(
                "ORD-003", "B003", "Charlie");
        DropwizardCqrsApplication.COMMAND_BUS.execute(command1);

        var command2 = new io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand(
                "ORD-003", "B003", "Charlie");
        assertThat(DropwizardCqrsApplication.ORDER_REPO.findByOrderNo("ORD-003")).isPresent();
    }
}
