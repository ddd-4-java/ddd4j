package io.ddd4j.sample.order.app;

import io.ddd4j.sample.order.client.command.ConfirmTradePaymentCmd;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;
import io.ddd4j.sample.order.client.command.TradeOrderItemCmd;
import io.ddd4j.sample.order.client.query.GoodsSpuQuery;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.gateway.TradeSession;
import io.ddd4j.sample.order.domain.event.TradeOrderPlaced;
import io.ddd4j.sample.order.domain.model.GoodsSpu;
import io.ddd4j.sample.order.domain.model.GoodsSkuOffer;
import io.ddd4j.sample.order.domain.model.TradeOrderItem;
import io.ddd4j.sample.order.domain.model.GoodsSku;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import io.ddd4j.sample.order.client.command.ChangeTradeOrderCmd;
import lombok.RequiredArgsConstructor;
import io.ddd4j.kit.lang.StrKit;

/** 编排商品目录和交易用例，使用租户事务和领域事件完成状态变更。 */
@RequiredArgsConstructor
public class CatalogService {
    /** \u6309\u79df\u6237\u6267\u884c\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u7684\u4e8b\u52a1\u5165\u53e3\u3002 */
    private final TradeSession.Transactions transactions;
    /** \u5728\u72b6\u6001\u63d0\u4ea4\u540e\u53d1\u5e03\u5546\u54c1\u4ea4\u6613\u4e8b\u4ef6\u7684\u7aef\u53e3\u3002 */
    private final DomainEventPublisher publisher;
    /** \u4e3a\u8ba2\u5355\u72b6\u6001\u53d8\u66f4\u63d0\u4f9b\u65f6\u95f4\u7684\u65f6\u949f\u3002 */
    private final Clock clock;

    /**
     * 创建商品及其初始规格。
     *
     * @param command 请求执行的业务命令
     * @return 已持久化的商品
     */
    public GoodsSpu create(CreateGoodsSpuCmd command) {
        String tenant = command.getTenantId();
        Long shopId = command.getShopId();
        if (Objects.isNull(shopId)) {
            throw new IllegalArgumentException("必须指定门店");
        }
        GoodsSku sku = GoodsSku.unlimited(tenant, command.getSkuCode(), command.getSkuName(), command.getSalePriceCent());
        GoodsSpu goods = GoodsSpu.listPackage(tenant, command.getSpuCode(), command.getSpuName(), shopId, sku);
        return transactions.execute(tenant, session -> session.goods().save(goods));
    }

    /**
     * 按门店可售规格创建交易订单。
     *
     * @param command 请求执行的业务命令
     * @return 已持久化的订单
     */
    public TradeOrder place(PlaceTradeOrderCmd command) {
        String tenant = command.getTenantId();
        Long shopId = command.getShopId();
        checkKey(tenant, command.getOrderNo());
        if (Objects.isNull(shopId) || shopId <= 0 || Objects.isNull(command.getItems()) || command.getItems().isEmpty()) {
            throw new IllegalArgumentException("订单必须指定有效门店并包含商品");
        }
        TradeOrder committed = transactions.execute(tenant, session -> {
            List<TradeOrderItem> lines = new ArrayList<>();
            for (TradeOrderItemCmd item : command.getItems()) {
                if (Objects.isNull(item)) {
                    throw new IllegalArgumentException("购买明细不能为空");
                }
                text(item.getSkuCode(), 64);
                GoodsSkuOffer offer = session.goods().findOffer(shopId, item.getSkuCode());
                lines.add(TradeOrderItem.fromOffer(tenant, lines.size() + 1, offer, item.getQuantity()));
            }
            return session.orders().save(TradeOrder.place(tenant, command.getOrderNo(), shopId, lines));
        });
        publisher.publish(new TradeOrderPlaced(committed));
        return committed;
    }

    /**
     * 记录订单支付结果并发布领域事件。
     *
     * @param command 请求执行的业务命令
     * @return 更新后的订单
     */
    public TradeOrder confirmPayment(ConfirmTradePaymentCmd command) {
        String tenant = command.getTenantId();
        String orderNo = command.getOrderNo();
        checkKey(tenant, orderNo);
        Long amount = command.getPaidAmountCent();
        if (Objects.isNull(amount) || amount < 0) {
            throw new IllegalArgumentException("支付金额必须为非负分值");
        }
        return change(tenant, orderNo, order -> order.confirmPayment(amount,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)));
    }

    /**
     * 按指令取消订单或完成履约。
     *
     * @param command 请求执行的业务命令
     * @return 更新后的订单
     */
    public TradeOrder change(ChangeTradeOrderCmd command) {
        return change(command.getTenantId(), command.getOrderNo(), switch (command.getAction()) {
            case CANCEL -> TradeOrder::cancel;
            case FULFILL -> TradeOrder::fulfill;
        });
    }

    /**
     * 按指令取消订单或完成履约。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @param transition 要执行的领域状态变更
     * @return 更新后的订单
     */
    private TradeOrder change(String tenant, String orderNo, UnaryOperator<TradeOrder> transition) {
        checkKey(tenant, orderNo);
        Changed result = transactions.execute(tenant, session -> {
            var orders = session.orders();
            TradeOrder previous = orders.lockByOrderNo(orderNo);
            TradeOrder changed = transition.apply(previous);
            if (changed == previous) {
                return new Changed(previous, List.of());
            }
            return new Changed(orders.updateById(changed), changed.pullDomainEvents());
        });
        result.getEvents().forEach(event -> publisher.publish(event));
        return result.getOrder();
    }

    /** \u4fdd\u5b58\u72b6\u6001\u53d8\u66f4\u540e\u7684\u8ba2\u5355\u548c\u5f85\u53d1\u5e03\u4e8b\u4ef6\uff0c\u4f9b\u4e8b\u52a1\u6210\u529f\u540e\u8fd4\u56de\u548c\u53d1\u5e03\u3002 */
    @lombok.Value
    private static class Changed {
        /** \u6210\u529f\u6301\u4e45\u5316\u540e\u8fd4\u56de\u7684\u8ba2\u5355\u805a\u5408\u3002 */
        TradeOrder order;
        /** \u4e8b\u52a1\u63d0\u4ea4\u540e\u5e94\u53d1\u5e03\u7684\u9886\u57df\u4e8b\u4ef6\u3002 */
        List<io.ddd4j.core.ddd.event.DomainEvent<?>> events;
    }

    /**
     * 按业务编号读取租户商品。
     *
     * @param query 包含租户和业务编号的查询条件
     * @return 查询到的商品
     */
    public GoodsSpu goods(GoodsSpuQuery query) {
        checkKey(query.getTenantId(), query.getSpuCode());
        return transactions.execute(query.getTenantId(), session -> session.goods().findByCode(query.getSpuCode()));
    }

    /**
     * 按业务编号读取租户订单。
     *
     * @param query 包含租户和业务编号的查询条件
     * @return 查询到的订单
     */
    public TradeOrder order(TradeOrderQuery query) {
        checkKey(query.getTenantId(), query.getOrderNo());
        return transactions.execute(query.getTenantId(), session -> session.orders().findByOrderNo(query.getOrderNo()));
    }

    /**
     * \u6821\u9a8c\u79df\u6237\u6807\u8bc6\u548c\u4e1a\u52a1\u7f16\u53f7\u7684\u957f\u5ea6\u53ca\u9996\u5c3e\u7a7a\u767d\u3002
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u7684\u5546\u54c1\u7f16\u7801\u6216\u8ba2\u5355\u53f7
     * @throws IllegalArgumentException \u6807\u8bc6\u4e3a\u7a7a\u767d\u3001\u8fc7\u957f\u6216\u542b\u9996\u5c3e\u7a7a\u767d
     */
    private static void checkKey(String tenant, String code) {
        text(tenant, 32);
        text(code, 64);
    }

    /**
     * \u8981\u6c42\u6587\u672c\u975e\u7a7a\u767d\u3001\u957f\u5ea6\u4e0d\u8d85\u8fc7\u9650\u5236\u4e14\u65e0\u9996\u5c3e\u7a7a\u767d\u3002
     * @param value \u5f85\u6821\u9a8c\u6587\u672c
     * @param max \u5b57\u7b26\u4e32\u957f\u5ea6\u4e0a\u9650
     * @throws IllegalArgumentException \u6587\u672c\u4e3a\u7a7a\u767d\u3001\u8fc7\u957f\u6216\u542b\u9996\u5c3e\u7a7a\u767d
     */
    private static void text(String value, int max) {
        if (StrKit.isBlank(value) || value.length() > max || !value.equals(value.strip())) {
            throw new IllegalArgumentException("名称或编码为空、超长或含首尾空白");
        }
    }
}
