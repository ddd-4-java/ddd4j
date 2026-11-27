package io.ddd4j.sample.order.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.domain.exception.TradeOrderStateConflict;
import io.ddd4j.sample.order.domain.gateway.GoodsSpuRepository;
import io.ddd4j.sample.order.domain.gateway.TradeOrderRepository;
import io.ddd4j.sample.order.domain.gateway.TradeSession;
import io.ddd4j.sample.order.domain.model.*;
import io.ddd4j.sample.order.infrastructure.persistence.po.GoodsSkuPO;
import io.ddd4j.sample.order.infrastructure.persistence.po.GoodsSpuPO;
import io.ddd4j.sample.order.infrastructure.persistence.po.TradeOrderItemPO;
import io.ddd4j.sample.order.infrastructure.persistence.po.TradeOrderPO;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * 在同一个 MyBatis 会话中提供商品和交易仓储。
 */
@RequiredArgsConstructor
public class MybatisRepositories implements TradeSession {
    /**
     * 用于读写商品 JSON 字符串数组的映射器。
     */
    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * 反序列化商品 JSON 字符串数组的目标类型。
     */
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    /**
     * 当前 MyBatis 会话绑定的商品交易映射器。
     */
    private final CatalogMapper mapper;
    /**
     * 当前仓储会话可访问的租户标识。
     */
    private final String tenant;

    /**
     * 将商品规格持久化行映射为领域对象。
     *
     * @param row 商品规格持久化记录
     * @return 商品规格领域对象
     */
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

    /**
     * 将成交明细持久化行映射为订单明细领域对象。
     *
     * @param row 成交明细持久化记录
     * @return 订单明细领域对象
     */
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

    /**
     * 将商品字符串列表写成数据库 JSON 数组。
     *
     * @param shopIds 要保存的字符串列表，可以为空引用
     * @return JSON 数组，空引用保留为空
     * @throws IllegalStateException 无法将列表序列化为 JSON
     */
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

    /**
     * 读取商品 JSON 数组并返回不可变列表。
     *
     * @param shopIds 数据库中的 JSON 数组，可以为空引用
     * @return 解析后的列表，空引用保留为空
     * @throws IllegalStateException 无法将 JSON 解析为字符串列表
     */
    private static List<String> readStringList(String shopIds) {
        if (Objects.isNull(shopIds)) {
            return null;
        }
        try {
            return List.copyOf(JSON.readValue(shopIds, STRING_LIST));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("无法读取字符串数组", failure);
        }
    }

    /**
     * 将数据库查询不到记录统一表示为缺失业务对象。
     *
     * @param <T>   查询结果类型
     * @param value 数据库查询结果
     * @return 查询到的业务对象
     * @throws NoSuchElementException 查询结果为空
     */
    private static <T> T required(T value) {
        return Optional.ofNullable(value).orElseThrow(() -> new NoSuchElementException("记录不存在或规格在此门店不可售"));
    }

    /**
     * 取得当前事务中的商品仓储。
     *
     * @return 商品仓储
     */
    @Override
    public GoodsSpuRepository goods() {
        return new GoodsStore();
    }

    /**
     * 取得当前事务中的订单仓储。
     *
     * @return 订单仓储
     */
    @Override
    public TradeOrderRepository orders() {
        return new OrderStore();
    }

    /**
     * 为当前租户写入商品及其规格。
     *
     * @param goods 待保存的商品聚合
     * @return 数据库生成的商品主键
     */
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

    /**
     * 根据商品行和所属规格行还原商品聚合。
     *
     * @param row 当前租户的商品持久化记录
     * @return 含规格的商品聚合
     */
    private GoodsSpu goods(GoodsSpuPO row) {
        List<GoodsSku> skus = mapper.skus(tenant, row.getId()).stream().map(MybatisRepositories::sku).toList();
        return GoodsSpu.restore(
                row.getId(), row.getTenantId(), row.getSpuCode(),
                row.getSpuName(), row.getProductKind(), row.getCategoryId(),
                readStringList(row.getImageUrls()), readStringList(row.getApplicableShopIds()), row.getReviewState(),
                row.getReviewNote(), row.getIsVisible(), row.getIsOnSale(),
                row.getCreatedBy(), row.getCreatedTime(), row.getUpdatedBy(),
                row.getUpdatedTime(), row.getDeleted(), skus);
    }

    /**
     * 写入订单和成交明细，给明细关联生成后的订单主键。
     *
     * @param order 待保存的订单聚合
     * @return 数据库生成的订单主键
     */
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

    /**
     * 根据订单行及其明细行还原订单聚合。
     *
     * @param row 当前租户的订单持久化记录
     * @return 含成交明细的订单聚合
     */
    private TradeOrder order(TradeOrderPO row) {
        List<TradeOrderItem> items = mapper.items(tenant, row.getId()).stream().map(MybatisRepositories::line).toList();
        return TradeOrder.restore(
                row.getId(), row.getTenantId(), row.getOrderNo(),
                row.getOrderKind(), row.getFulfillmentMode(), row.getShopId(),
                row.getBuyerId(), row.getOrderState(), row.getFulfillmentState(),
                row.getRefundState(), row.getPlacedTime(), row.getPaidTime(),
                row.getGrossAmountCent(), row.getMerchantDiscountCent(), row.getPaidAmountCent(),
                row.getCreatedBy(), row.getCreatedTime(), row.getUpdatedBy(),
                row.getUpdatedTime(), row.getDeleted(), items);
    }

    /**
     * 在当前租户会话内读写商品聚合和可售规格。
     */
    private final class GoodsStore implements GoodsSpuRepository {
        /**
         * 按主键读取领域对象或持久化记录。
         *
         * <p>此实现调用 <code>Optional.ofNullable(mapper.goodsById(tenant, id))</code>。</p>
         *
         * @param id 当前操作使用的记录主键或业务标识
         * @return 此操作产生的<code>Optional&lt;GoodsSpu&gt;</code>结果
         */
        @Override
        public Optional<GoodsSpu> findById(Long id) {
            return Optional.ofNullable(mapper.goodsById(tenant, id)).map(MybatisRepositories.this::goods);
        }

        /**
         * 写入或更新给定的领域对象或存储记录。
         *
         * <p>满足 <code>Objects.nonNull(goods.id())</code> 时抛出 <code>UnsupportedOperationException</code>。 此实现调用 <code>goods.id()</code>。</p>
         *
         * @param goods 传给 <code>goods.id()</code>
         * @return 实际返回<code>findById(insertGoods(goods)).orElseThrow()</code>
         */
        @Override
        public GoodsSpu save(GoodsSpu goods) {
            if (Objects.nonNull(goods.id())) {
                throw new UnsupportedOperationException("商品建档用例只保存新聚合");
            }
            return findById(insertGoods(goods)).orElseThrow();
        }

        /**
         * 按业务编码查找对应记录。
         *
         * <p>此实现调用 <code>goods(required(mapper.goods(tenant, code)))</code>。</p>
         *
         * @param code 业务编号
         * @return 实际返回<code>goods(required(mapper.goods(tenant, code)))</code>
         */
        @Override
        public GoodsSpu findByCode(String code) {
            return goods(required(mapper.goods(tenant, code)));
        }

        /**
         * 查询指定商品规格的在售数据。
         *
         * <p>此实现调用 <code>required(mapper.offer(tenant, shopId, skuCode))</code>。</p>
         *
         * @param shopId  门店主键
         * @param skuCode 租户内 SKU 编号
         * @return 实际返回<code>required(mapper.offer(tenant, shopId, skuCode))</code>
         */
        @Override
        public GoodsSkuOffer findOffer(long shopId, String skuCode) {
            return required(mapper.offer(tenant, shopId, skuCode));
        }
    }

    /**
     * 在当前租户会话内读写订单聚合及其明细。
     */
    private final class OrderStore implements TradeOrderRepository {
        /**
         * 按主键读取领域对象或持久化记录。
         *
         * <p>此实现调用 <code>Optional.ofNullable(mapper.orderById(tenant, id))</code>。</p>
         *
         * @param id 当前操作使用的记录主键或业务标识
         * @return 此操作产生的<code>Optional&lt;TradeOrder&gt;</code>结果
         */
        @Override
        public Optional<TradeOrder> findById(Long id) {
            return Optional.ofNullable(mapper.orderById(tenant, id)).map(MybatisRepositories.this::order);
        }

        /**
         * 写入或更新给定的领域对象或存储记录。
         *
         * <p>满足 <code>Objects.nonNull(order.id())</code> 时抛出 <code>UnsupportedOperationException</code>。 此实现调用 <code>order.id()</code>。</p>
         *
         * @param order 传给 <code>order.id()</code>
         * @return 实际返回<code>findById(insertOrder(order)).orElseThrow()</code>
         */
        @Override
        public TradeOrder save(TradeOrder order) {
            if (Objects.nonNull(order.id())) {
                throw new UnsupportedOperationException("下单用例只保存新聚合");
            }
            return findById(insertOrder(order)).orElseThrow();
        }

        /**
         * 按业务订单号读取并锁定订单记录。
         *
         * <p>此实现调用 <code>order(required(mapper.lockOrder(tenant, orderNo)))</code>。</p>
         *
         * @param orderNo 租户内订单编号
         * @return 实际返回<code>order(required(mapper.lockOrder(tenant, orderNo)))</code>
         */
        @Override
        public TradeOrder lockByOrderNo(String orderNo) {
            return order(required(mapper.lockOrder(tenant, orderNo)));
        }

        /**
         * 通过<code>row.setTenantId(tenant)</code>、<code>row.setId(order.id())</code>处理当前调用。
         *
         * @param order 传给 <code>row.setId(order.id())</code>
         * @return 实际返回<code>findById(order.id()).orElseThrow()</code>
         */
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
            return findById(order.id()).orElseThrow();
        }

        /**
         * 按业务订单号查找订单记录。
         *
         * <p>此实现调用 <code>order(required(mapper.order(tenant, code)))</code>。</p>
         *
         * @param code 传给 <code>order(required(mapper.order(tenant, code)))</code>
         * @return 实际返回<code>order(required(mapper.order(tenant, code)))</code>
         */
        @Override
        public TradeOrder findByOrderNo(String code) {
            return order(required(mapper.order(tenant, code)));
        }
    }
}
