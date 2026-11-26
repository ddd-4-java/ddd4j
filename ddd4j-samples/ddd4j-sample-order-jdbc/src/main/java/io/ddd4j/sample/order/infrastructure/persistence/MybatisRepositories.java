package io.ddd4j.sample.order.infrastructure.persistence;

import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collections;
import io.ddd4j.sample.order.domain.gateway.TradeSession;
import io.ddd4j.sample.order.domain.gateway.GoodsSpuRepository;
import io.ddd4j.sample.order.domain.gateway.TradeOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import io.ddd4j.sample.order.domain.model.GoodsSpu;
import io.ddd4j.sample.order.domain.model.GoodsSkuOffer;
import io.ddd4j.sample.order.domain.model.TradeOrderItem;
import io.ddd4j.sample.order.domain.model.GoodsSku;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import io.ddd4j.sample.order.domain.model.TradeOrderStateConflict;
import io.ddd4j.sample.order.infrastructure.persistence.po.GoodsSkuPO;
import io.ddd4j.sample.order.infrastructure.persistence.po.GoodsSpuPO;
import io.ddd4j.sample.order.infrastructure.persistence.po.TradeOrderItemPO;
import io.ddd4j.sample.order.infrastructure.persistence.po.TradeOrderPO;
import java.util.List;
import java.util.Optional;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MybatisRepositories implements TradeSession {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() { };

    private final CatalogMapper mapper;
    private final String tenant;

    @Override
    public GoodsSpuRepository goods() {
        return new GoodsStore();
    }

    @Override
    public TradeOrderRepository orders() {
        return new OrderStore();
    }

    private final class GoodsStore implements GoodsSpuRepository {
        @Override
        public Optional<GoodsSpu> findById(Long id) {
            return Optional.ofNullable(mapper.goodsById(tenant, id)).map(MybatisRepositories.this::goods);
        }

        @Override
        public GoodsSpu save(GoodsSpu goods) {
            if (Objects.nonNull(goods.id())) {
                throw new UnsupportedOperationException("商品建档用例只保存新聚合");
            }
            return findById(insertGoods(goods)).orElseThrow(NoSuchElementException::new);
        }

        @Override
        public GoodsSpu findByCode(String code) {
            return goods(required(mapper.goods(tenant, code)));
        }

        @Override
        public GoodsSkuOffer findOffer(long shopId, String skuCode) {
            return required(mapper.offer(tenant, shopId, skuCode));
        }
    }

    private final class OrderStore implements TradeOrderRepository {
        @Override
        public Optional<TradeOrder> findById(Long id) {
            return Optional.ofNullable(mapper.orderById(tenant, id)).map(MybatisRepositories.this::order);
        }

        @Override
        public TradeOrder save(TradeOrder order) {
            if (Objects.nonNull(order.id())) {
                throw new UnsupportedOperationException("下单用例只保存新聚合");
            }
            return findById(insertOrder(order)).orElseThrow(NoSuchElementException::new);
        }

        @Override
        public TradeOrder lockByOrderNo(String orderNo) {
            return order(required(mapper.lockOrder(tenant, orderNo)));
        }

        @Override
        public TradeOrder updateById(TradeOrder order) {
            TradeOrderPO row = new TradeOrderPO();
            row.setTenantId(tenant);
            row.setId(order.id());
            row.setOrderState(order.getOrderState());
            row.setFulfillmentState(order.getFulfillmentState());
            row.setRefundState(order.getRefundState());
            row.setPaidAmountCent(order.getPaidAmountCent());
            row.setPaidTime(order.getPaidTime());
            if (mapper.updateTradeState(row) != 1) {
                throw new TradeOrderStateConflict("订单已发生状态变化");
            }
            return findById(order.id()).orElseThrow(NoSuchElementException::new);
        }

        @Override
        public TradeOrder findByOrderNo(String code) {
            return order(required(mapper.order(tenant, code)));
        }
    }

    private Long insertGoods(GoodsSpu goods) {
        GoodsSpuPO row = new GoodsSpuPO();
        row.setTenantId(tenant);
        row.setSpuCode(goods.getSpuCode());
        row.setSpuName(goods.getSpuName());
        row.setProductKind(goods.getProductKind());
        row.setReviewState(goods.getReviewState());
        row.setIsVisible(goods.getIsVisible());
        row.setIsOnSale(goods.getIsOnSale());
        row.setApplicableShopIds(writeStringList(goods.getApplicableShopIds()));
        mapper.insertGoods(row);
        for (GoodsSku sku : goods.getSkus()) {
            GoodsSkuPO child = new GoodsSkuPO();
            child.setTenantId(tenant);
            child.setSpuId(row.getId());
            child.setSkuCode(sku.getSkuCode());
            child.setSkuName(sku.getSkuName());
            child.setSalePriceCent(sku.getSalePriceCent());
            child.setStockMode(sku.getStockMode());
            mapper.insertSku(child);
        }
        return row.getId();
    }

    private GoodsSpu goods(GoodsSpuPO row) {
        List<GoodsSku> skus = mapper.skus(tenant, row.getId()).stream().map(MybatisRepositories::sku).collect(Collectors.toList());
        return GoodsSpu.restore(
                row.getId(), row.getTenantId(), row.getSpuCode(),
                row.getSpuName(), row.getProductKind(), row.getCategoryId(),
                readStringList(row.getImageUrls()), readStringList(row.getApplicableShopIds()), row.getReviewState(),
                row.getReviewNote(), row.getIsVisible(), row.getIsOnSale(),
                row.getCreatedBy(), row.getCreatedTime(), row.getUpdatedBy(),
                row.getUpdatedTime(), row.getDeleted(), skus);
    }

    private static GoodsSku sku(GoodsSkuPO row) {
        return GoodsSku.builder()
                .id(row.getId())
                .tenantId(row.getTenantId())
                .spuId(row.getSpuId())
                .skuCode(row.getSkuCode())
                .skuName(row.getSkuName())
                .salePriceCent(row.getSalePriceCent())
                .listPriceCent(row.getListPriceCent())
                .stockMode(row.getStockMode())
                .stockLimit(row.getStockLimit())
                .createdBy(row.getCreatedBy())
                .createdTime(row.getCreatedTime())
                .updatedBy(row.getUpdatedBy())
                .updatedTime(row.getUpdatedTime())
                .deleted(row.getDeleted())
                .build();
    }

    private Long insertOrder(TradeOrder order) {
        TradeOrderPO row = new TradeOrderPO();
        row.setTenantId(tenant);
        row.setOrderNo(order.getOrderNo());
        row.setShopId(order.getShopId());
        row.setGrossAmountCent(order.getGrossAmountCent());
        row.setOrderKind(order.getOrderKind());
        row.setFulfillmentMode(order.getFulfillmentMode());
        row.setOrderState(order.getOrderState());
        row.setFulfillmentState(order.getFulfillmentState());
        row.setRefundState(order.getRefundState());
        mapper.insertOrder(row);
        for (TradeOrderItem line : order.getItems()) {
            TradeOrderItemPO child = new TradeOrderItemPO();
            child.setTenantId(tenant);
            child.setOrderId(row.getId());
            child.setLineNo(line.getLineNo());
            child.setSkuId(line.getSkuId());
            child.setProductNameSnapshot(line.getProductNameSnapshot());
            child.setSkuNameSnapshot(line.getSkuNameSnapshot());
            child.setQuantity(line.getQuantity());
            child.setUnitPriceCent(line.getUnitPriceCent());
            mapper.insertItem(child);
        }
        return row.getId();
    }

    private TradeOrder order(TradeOrderPO row) {
        List<TradeOrderItem> items = mapper.items(tenant, row.getId()).stream().map(MybatisRepositories::line).collect(Collectors.toList());
        return TradeOrder.restore(
                row.getId(), row.getTenantId(), row.getOrderNo(),
                row.getOrderKind(), row.getFulfillmentMode(), row.getShopId(),
                row.getBuyerId(), row.getOrderState(), row.getFulfillmentState(),
                row.getRefundState(), row.getPlacedTime(), row.getPaidTime(),
                row.getGrossAmountCent(), row.getMerchantDiscountCent(), row.getPaidAmountCent(),
                row.getCreatedBy(), row.getCreatedTime(), row.getUpdatedBy(),
                row.getUpdatedTime(), row.getDeleted(), items);
    }

    private static TradeOrderItem line(TradeOrderItemPO row) {
        return TradeOrderItem.builder()
                .id(row.getId())
                .tenantId(row.getTenantId())
                .orderId(row.getOrderId())
                .lineNo(row.getLineNo())
                .skuId(row.getSkuId())
                .productNameSnapshot(row.getProductNameSnapshot())
                .skuNameSnapshot(row.getSkuNameSnapshot())
                .quantity(row.getQuantity())
                .unitPriceCent(row.getUnitPriceCent())
                .createdBy(row.getCreatedBy())
                .createdTime(row.getCreatedTime())
                .updatedBy(row.getUpdatedBy())
                .updatedTime(row.getUpdatedTime())
                .deleted(row.getDeleted())
                .build();
    }

    private static String writeStringList(List<String> shopIds) {
        if (Objects.isNull(shopIds)) {
            return null;
        }
        try {
            return JSON.writeValueAsString(shopIds);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("无法写入字符串数组", failure);
        }
    }

    private static List<String> readStringList(String shopIds) {
        if (Objects.isNull(shopIds)) {
            return null;
        }
        try {
            return Collections.unmodifiableList(new ArrayList<>(JSON.readValue(shopIds, STRING_LIST)));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("无法读取字符串数组", failure);
        }
    }

    private static <T> T required(T value) {
        return Optional.ofNullable(value).orElseThrow(() -> new NoSuchElementException("记录不存在或规格在此门店不可售"));
    }
}
