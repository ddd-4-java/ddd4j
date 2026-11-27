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

/** 在同一个 MyBatis 会话中提供商品和交易仓储。 */
@RequiredArgsConstructor
public class MybatisRepositories implements TradeSession {
    /** \u7528\u4e8e\u8bfb\u5199\u5546\u54c1 JSON \u5b57\u7b26\u4e32\u6570\u7ec4\u7684\u6620\u5c04\u5668\u3002 */
    private static final ObjectMapper JSON = new ObjectMapper();
    /** \u53cd\u5e8f\u5217\u5316\u5546\u54c1 JSON \u5b57\u7b26\u4e32\u6570\u7ec4\u7684\u76ee\u6807\u7c7b\u578b\u3002 */
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() { };

    /** \u5f53\u524d MyBatis \u4f1a\u8bdd\u7ed1\u5b9a\u7684\u5546\u54c1\u4ea4\u6613\u6620\u5c04\u5668\u3002 */
    private final CatalogMapper mapper;
    /** \u5f53\u524d\u4ed3\u50a8\u4f1a\u8bdd\u53ef\u8bbf\u95ee\u7684\u79df\u6237\u6807\u8bc6\u3002 */
    private final String tenant;

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

    /** \u5728\u5f53\u524d\u79df\u6237\u4f1a\u8bdd\u5185\u8bfb\u5199\u5546\u54c1\u805a\u5408\u548c\u53ef\u552e\u89c4\u683c\u3002 */
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
         * @return 此操作产生的<code>GoodsSpu</code>结果
         */
        @Override
        public GoodsSpu save(GoodsSpu goods) {
            if (Objects.nonNull(goods.id())) {
                throw new UnsupportedOperationException("商品建档用例只保存新聚合");
            }
            return findById(insertGoods(goods)).orElseThrow(NoSuchElementException::new);
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
         * @param shopId 门店主键
         * @param skuCode 租户内 SKU 编号
         * @return 实际返回<code>required(mapper.offer(tenant, shopId, skuCode))</code>
         */
        @Override
        public GoodsSkuOffer findOffer(long shopId, String skuCode) {
            return required(mapper.offer(tenant, shopId, skuCode));
        }
    }

    /** \u5728\u5f53\u524d\u79df\u6237\u4f1a\u8bdd\u5185\u8bfb\u5199\u8ba2\u5355\u805a\u5408\u53ca\u5176\u660e\u7ec6\u3002 */
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
         * @return 此操作产生的<code>TradeOrder</code>结果
         */
        @Override
        public TradeOrder save(TradeOrder order) {
            if (Objects.nonNull(order.id())) {
                throw new UnsupportedOperationException("下单用例只保存新聚合");
            }
            return findById(insertOrder(order)).orElseThrow(NoSuchElementException::new);
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
         * @return 此操作产生的<code>TradeOrder</code>结果
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
            return findById(order.id()).orElseThrow(NoSuchElementException::new);
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

    /**
     * \u4e3a\u5f53\u524d\u79df\u6237\u5199\u5165\u5546\u54c1\u53ca\u5176\u89c4\u683c\u3002
     * @param goods \u5f85\u4fdd\u5b58\u7684\u5546\u54c1\u805a\u5408
     * @return \u6570\u636e\u5e93\u751f\u6210\u7684\u5546\u54c1\u4e3b\u952e
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
     * \u6839\u636e\u5546\u54c1\u884c\u548c\u6240\u5c5e\u89c4\u683c\u884c\u8fd8\u539f\u5546\u54c1\u805a\u5408\u3002
     * @param row \u5f53\u524d\u79df\u6237\u7684\u5546\u54c1\u6301\u4e45\u5316\u8bb0\u5f55
     * @return \u542b\u89c4\u683c\u7684\u5546\u54c1\u805a\u5408
     */
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

    /**
     * \u5c06\u5546\u54c1\u89c4\u683c\u6301\u4e45\u5316\u884c\u6620\u5c04\u4e3a\u9886\u57df\u5bf9\u8c61\u3002
     * @param row \u5546\u54c1\u89c4\u683c\u6301\u4e45\u5316\u8bb0\u5f55
     * @return \u5546\u54c1\u89c4\u683c\u9886\u57df\u5bf9\u8c61
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
     * \u5199\u5165\u8ba2\u5355\u548c\u6210\u4ea4\u660e\u7ec6\uff0c\u7ed9\u660e\u7ec6\u5173\u8054\u751f\u6210\u540e\u7684\u8ba2\u5355\u4e3b\u952e\u3002
     * @param order \u5f85\u4fdd\u5b58\u7684\u8ba2\u5355\u805a\u5408
     * @return \u6570\u636e\u5e93\u751f\u6210\u7684\u8ba2\u5355\u4e3b\u952e
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
     * \u6839\u636e\u8ba2\u5355\u884c\u53ca\u5176\u660e\u7ec6\u884c\u8fd8\u539f\u8ba2\u5355\u805a\u5408\u3002
     * @param row \u5f53\u524d\u79df\u6237\u7684\u8ba2\u5355\u6301\u4e45\u5316\u8bb0\u5f55
     * @return \u542b\u6210\u4ea4\u660e\u7ec6\u7684\u8ba2\u5355\u805a\u5408
     */
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

    /**
     * \u5c06\u6210\u4ea4\u660e\u7ec6\u6301\u4e45\u5316\u884c\u6620\u5c04\u4e3a\u8ba2\u5355\u660e\u7ec6\u9886\u57df\u5bf9\u8c61\u3002
     * @param row \u6210\u4ea4\u660e\u7ec6\u6301\u4e45\u5316\u8bb0\u5f55
     * @return \u8ba2\u5355\u660e\u7ec6\u9886\u57df\u5bf9\u8c61
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
     * \u5c06\u5546\u54c1\u5b57\u7b26\u4e32\u5217\u8868\u5199\u6210\u6570\u636e\u5e93 JSON \u6570\u7ec4\u3002
     * @param shopIds \u8981\u4fdd\u5b58\u7684\u5b57\u7b26\u4e32\u5217\u8868\uff0c\u53ef\u4ee5\u4e3a\u7a7a\u5f15\u7528
     * @return JSON \u6570\u7ec4\uff0c\u7a7a\u5f15\u7528\u4fdd\u7559\u4e3a\u7a7a
     * @throws IllegalStateException \u65e0\u6cd5\u5c06\u5217\u8868\u5e8f\u5217\u5316\u4e3a JSON
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
     * \u8bfb\u53d6\u5546\u54c1 JSON \u6570\u7ec4\u5e76\u8fd4\u56de\u4e0d\u53ef\u53d8\u5217\u8868\u3002
     * @param shopIds \u6570\u636e\u5e93\u4e2d\u7684 JSON \u6570\u7ec4\uff0c\u53ef\u4ee5\u4e3a\u7a7a\u5f15\u7528
     * @return \u89e3\u6790\u540e\u7684\u5217\u8868\uff0c\u7a7a\u5f15\u7528\u4fdd\u7559\u4e3a\u7a7a
     * @throws IllegalStateException \u65e0\u6cd5\u5c06 JSON \u89e3\u6790\u4e3a\u5b57\u7b26\u4e32\u5217\u8868
     */
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

    /**
     * \u5c06\u6570\u636e\u5e93\u67e5\u8be2\u4e0d\u5230\u8bb0\u5f55\u7edf\u4e00\u8868\u793a\u4e3a\u7f3a\u5931\u4e1a\u52a1\u5bf9\u8c61\u3002
     * @param <T> \u67e5\u8be2\u7ed3\u679c\u7c7b\u578b
     * @param value \u6570\u636e\u5e93\u67e5\u8be2\u7ed3\u679c
     * @return \u67e5\u8be2\u5230\u7684\u4e1a\u52a1\u5bf9\u8c61
     * @throws NoSuchElementException \u67e5\u8be2\u7ed3\u679c\u4e3a\u7a7a
     */
    private static <T> T required(T value) {
        return Optional.ofNullable(value).orElseThrow(() -> new NoSuchElementException("记录不存在或规格在此门店不可售"));
    }
}
