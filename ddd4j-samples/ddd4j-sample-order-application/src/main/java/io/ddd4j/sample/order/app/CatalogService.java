package io.ddd4j.sample.order.app;

import io.ddd4j.sample.order.domain.gateway.TradeOrderRepository;
import java.util.Collections;
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

@RequiredArgsConstructor
public class CatalogService {
    private final TradeSession.Transactions transactions;
    private final DomainEventPublisher publisher;
    private final Clock clock;

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

    public TradeOrder change(ChangeTradeOrderCmd command) {
        switch (command.getAction()) {
            case CANCEL:
                return change(command.getTenantId(), command.getOrderNo(), TradeOrder::cancel);
            case FULFILL:
                return change(command.getTenantId(), command.getOrderNo(), TradeOrder::fulfill);
            default:
                throw new IllegalArgumentException("不支持的订单状态变更");
        }
    }

    private TradeOrder change(String tenant, String orderNo, UnaryOperator<TradeOrder> transition) {
        checkKey(tenant, orderNo);
        Changed result = transactions.execute(tenant, session -> {
            TradeOrderRepository orders = session.orders();
            TradeOrder previous = orders.lockByOrderNo(orderNo);
            TradeOrder changed = transition.apply(previous);
            if (changed == previous) {
                return new Changed(previous, Collections.emptyList());
            }
            return new Changed(orders.updateById(changed), changed.pullDomainEvents());
        });
        result.getEvents().forEach(event -> publisher.publish(event));
        return result.getOrder();
    }

    @lombok.Value
    private static class Changed {
        TradeOrder order;
        List<io.ddd4j.core.ddd.event.DomainEvent<?>> events;
    }

    public GoodsSpu goods(GoodsSpuQuery query) {
        checkKey(query.getTenantId(), query.getSpuCode());
        return transactions.execute(query.getTenantId(), session -> session.goods().findByCode(query.getSpuCode()));
    }

    public TradeOrder order(TradeOrderQuery query) {
        checkKey(query.getTenantId(), query.getOrderNo());
        return transactions.execute(query.getTenantId(), session -> session.orders().findByOrderNo(query.getOrderNo()));
    }

    private static void checkKey(String tenant, String code) {
        text(tenant, 32);
        text(code, 64);
    }

    private static void text(String value, int max) {
        if (StrKit.isBlank(value) || value.length() > max || !value.equals(StrKit.trimWhitespace(value))) {
            throw new IllegalArgumentException("名称或编码为空、超长或含首尾空白");
        }
    }
}
