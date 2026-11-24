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
package io.ddd4j.sample.order.testkit;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderStatus;
import io.ddd4j.sample.order.domain.event.OrderCreatedEvent;
import io.ddd4j.sample.order.domain.event.OrderLineAddedEvent;
import io.ddd4j.sample.order.domain.event.OrderPaidEvent;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 四类样例共用的行为断言；共享夹具通过只证明断言能力，不能证明平台运行验收。 */
public final class SampleContractAssertions {
    private SampleContractAssertions() {
    }

    /**
     * 验证无可选运行时服务的默认订单保存、更新和过滤查询；保留同步读模型及本地 Outbox。
     * 调用方必须在未启用 bus/ES/后台投影/MQ/auth 的实际平台中创建驱动。
     * @param driver 默认样例驱动
     * @param orderNo 本次唯一订单号
     */
    public static void defaultOrder(OrderSampleDriver driver, String orderNo) {
        String id = create(driver, orderNo);
        order(driver.find(id), id, orderNo, OrderStatus.DRAFT, "0");
        driver.addLine(line(id));
        order(driver.find(id), id, orderNo, OrderStatus.DRAFT, "119.80");
        driver.pay(id, "payment-" + orderNo);
        order(driver.find(id), id, orderNo, OrderStatus.PAID, "119.80");
        queried(driver, id, orderNo);
    }

    /**
     * 验证命令写模型/事件、独立投影、失败位置不推进及重试恢复。
     * 驱动必须控制调度，使用隔离的投影流；project 等待实际批次完成，不能固定返回成功。
     * @param driver CQRS 驱动
     * @param orderNo 本次唯一订单号
     */
    public static void cqrsOrder(CqrsOrderSampleDriver driver, String orderNo) {
        long initialPosition = driver.position();
        String id = create(driver, orderNo);
        driver.addLine(line(id));
        driver.pay(id, "payment-" + orderNo);
        order(driver.persisted(id), id, orderNo, OrderStatus.PAID, "119.80");
        List<OutboxMessage> events = driver.events(id);
        check(events.size() == 3, "CQRS must persist three order events");
        Set<String> types = events.stream().map(OutboxMessage::eventType).collect(Collectors.toSet());
        Set<String> expectedTypes = new HashSet<>(Arrays.asList(OrderCreatedEvent.class.getName(),
                OrderLineAddedEvent.class.getName(), OrderPaidEvent.class.getName()));
        check(types.equals(expectedTypes), "CQRS event types must match commands");
        check(events.stream().allMatch(event -> id.equals(event.aggregateId())
                && StrKit.isNotBlank(event.id()) && Objects.nonNull(event.payload())),
                "CQRS events must contain actual order payloads and identities");
        check(!driver.projection(id).isPresent(), "projection must not read the synchronous write-model fallback");
        check(driver.position() == initialPosition, "commands must not advance projection position");
        driver.failNextProjection();
        failure(IllegalStateException.class, driver::project, "injected projection failure must propagate");
        check(driver.position() == initialPosition, "failed projection must not advance source position");
        check(!driver.projection(id).isPresent(), "failed projection must not create a query row");
        driver.project();
        long recoveredPosition = driver.position();
        check(recoveredPosition > initialPosition, "retry must advance persisted source position");
        order(driver.projection(id).orElseThrow(() -> new AssertionError("retry must create query row")),
                id, orderNo, OrderStatus.PAID, "119.80");
        order(driver.find(id), id, orderNo, OrderStatus.PAID, "119.80");
        queried(driver, id, orderNo);
        driver.project();
        check(driver.position() == recoveredPosition, "empty retry must keep source position");
        queried(driver, id, orderNo);
    }

    /**
     * 对 Sa-Token 或 Shiro 执行登录、认证、授权允许/拒绝、业务异常、退出失效及请求清理断言。
     * 测试账号应只具有 allowedPermission；平台适配器保留原生 HTTP 格式并从真实响应提取资源。
     * @param engine satoken 或 shiro
     * @param driver 认证驱动
     * @param username 测试账号
     * @param password 测试密码
     * @param expectedPrincipal 实际受保护入口应返回的主体标识，可与登录账号不同
     * @param allowedPermission 允许权限
     * @param deniedPermission 拒绝权限
     * @param expectedResource 允许访问时的业务资源
     */
    public static void authentication(String engine, AuthSampleDriver driver, String username,
            String password, String expectedPrincipal, String allowedPermission, String deniedPermission, String expectedResource) {
        check("satoken".equals(engine) || "shiro".equals(engine), "unsupported auth scenario name");
        clean(driver, engine + " initial context");
        String token = driver.login(username, password);
        check(StrKit.isNotBlank(token), engine + " login must return a token");
        clean(driver, engine + " login cleanup");
        check(Objects.equals(expectedPrincipal, driver.authenticatedPrincipal(token)), engine + " authenticated principal");
        clean(driver, engine + " authenticated request cleanup");
        check(Objects.equals(expectedResource, driver.authorizedResource(token, allowedPermission)),
                engine + " allowed resource");
        clean(driver, engine + " allowed request cleanup");
        failure(SecurityException.class, () -> driver.authorizedResource(token, deniedPermission),
                engine + " denied permission must not return a resource");
        clean(driver, engine + " denied request cleanup");
        failure(IllegalStateException.class, () -> driver.failingRequest(token),
                engine + " injected business failure must propagate");
        clean(driver, engine + " exceptional request cleanup");
        driver.logout(token);
        clean(driver, engine + " logout cleanup");
        failure(SecurityException.class, () -> driver.authenticatedPrincipal(token),
                engine + " logged-out token must be rejected");
        clean(driver, engine + " invalid token request cleanup");
    }

    private static String create(OrderSampleDriver driver, String orderNo) {
        String id = driver.create(new CreateOrderCommand(orderNo, "buyer-1", "Alice"));
        check(StrKit.isNotBlank(id), "create must return actual order id");
        return id;
    }

    private static AddOrderLineCommand line(String id) {
        return new AddOrderLineCommand(id, "goods-1", "DDD Book", 2, new BigDecimal("59.90"));
    }

    private static void queried(OrderSampleDriver driver, String id, String orderNo) {
        List<OrderReadModel> paid = driver.query(new OrderQuery("buyer-1", OrderStatus.PAID, 1, 100));
        List<OrderReadModel> matches = paid.stream().filter(row -> id.equals(row.id())).collect(Collectors.toList());
        check(matches.size() == 1, "paid query must contain exactly one matching order");
        order(matches.get(0), id, orderNo, OrderStatus.PAID, "119.80");
        check(driver.query(new OrderQuery("buyer-1", OrderStatus.DRAFT, 1, 100)).stream()
                .noneMatch(row -> id.equals(row.id())), "query must apply status filter");
        check(driver.query(new OrderQuery("other-buyer", OrderStatus.PAID, 1, 100)).stream()
                .noneMatch(row -> id.equals(row.id())), "query must apply buyer filter");
    }

    private static void order(OrderReadModel actual, String id, String orderNo, OrderStatus status, String total) {
        check(Objects.nonNull(actual), "order must exist");
        check(id.equals(actual.id()) && orderNo.equals(actual.orderNo()), "order identity must match request");
        check("buyer-1".equals(actual.buyerId()) && "Alice".equals(actual.buyerName()), "order buyer must persist");
        check(status == actual.status(), "order status must be " + status);
        check(Objects.nonNull(actual.totalAmount()) && new BigDecimal(total).compareTo(actual.totalAmount()) == 0,
                "order total must be " + total);
    }

    private static void clean(AuthSampleDriver driver, String message) {
        check(!driver.contextPrincipal().isPresent(), message);
    }

    private static void failure(Class<? extends RuntimeException> type, Runnable operation, String message) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            if (type.isInstance(exception)) {
                return;
            }
            throw new AssertionError(message + ": unexpected exception", exception);
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
