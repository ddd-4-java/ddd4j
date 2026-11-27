package io.ddd4j.sample.order.app;

import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.client.command.*;
import io.ddd4j.sample.order.client.query.GoodsSpuQuery;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.event.TradeOrderPlaced;
import io.ddd4j.sample.order.domain.gateway.TradeSession;
import io.ddd4j.sample.order.domain.model.*;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * 编排商品目录和交易用例，使用租户事务和领域事件完成状态变更。
 */
@RequiredArgsConstructor
public class CatalogService {
    /**
     * 按租户执行商品交易操作的事务入口。
     */
    private final TradeSession.Transactions transactions;
    /**
     * 在状态提交后发布商品交易事件的端口。
     */
    private final DomainEventPublisher publisher;
    /**
     * 为订单状态变更提供时间的时钟。
     */
    private final Clock clock;

    /**
     * 校验租户标识和业务编号的长度及首尾空白。
     *
     * @param tenant 请求所属租户标识
     * @param code   租户内的商品编码或订单号
     * @throws IllegalArgumentException 标识为空白、过长或含首尾空白
     */
    private static void checkKey(String tenant, String code) {
        text(tenant, 32);
        text(code, 64);
    }

    /**
     * 要求文本非空白、长度不超过限制且无首尾空白。
     *
     * @param value 待校验文本
     * @param max   字符串长度上限
     * @throws IllegalArgumentException 文本为空白、过长或含首尾空白
     */
    private static void text(String value, int max) {
        if (StrKit.isBlank(value) || value.length() > max || !value.equals(value.strip())) {
            throw new IllegalArgumentException("名称或编码为空、超长或含首尾空白");
        }
    }

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
     * @param tenant     租户编号
     * @param orderNo    租户内订单编号
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
     * 保存状态变更后的订单和待发布事件，供事务成功后返回和发布。
     */
    @lombok.Value
    private static class Changed {
        /**
         * 成功持久化后返回的订单聚合。
         */
        TradeOrder order;
        /**
         * 事务提交后应发布的领域事件。
         */
        List<io.ddd4j.core.ddd.event.DomainEvent<?>> events;
    }
}
