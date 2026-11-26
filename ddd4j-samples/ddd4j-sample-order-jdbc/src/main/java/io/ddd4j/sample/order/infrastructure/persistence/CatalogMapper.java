package io.ddd4j.sample.order.infrastructure.persistence;

import io.ddd4j.sample.order.infrastructure.persistence.po.GoodsSkuPO;
import io.ddd4j.sample.order.infrastructure.persistence.po.GoodsSpuPO;
import io.ddd4j.sample.order.infrastructure.persistence.po.TradeOrderItemPO;
import io.ddd4j.sample.order.infrastructure.persistence.po.TradeOrderPO;
import io.ddd4j.sample.order.domain.model.GoodsSkuOffer;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 四张商品交易表的 MyBatis SQL 映射。 */
public interface CatalogMapper {
    /**
     * 插入商品主数据。
     *
     * @param row 映射到业务表的数据行
     */
    @Insert("""
        INSERT INTO goods_spu (tenant_id,spu_code,spu_name,product_kind,applicable_shop_ids,review_state,is_visible,is_on_sale)
        VALUES (#{tenantId},#{spuCode},#{spuName},#{productKind},#{applicableShopIds},#{reviewState},#{isVisible},#{isOnSale})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertGoods(GoodsSpuPO row);

    /**
     * 插入商品规格。
     *
     * @param row 映射到业务表的数据行
     */
    @Insert("""
        INSERT INTO goods_sku (tenant_id,spu_id,sku_code,sku_name,sale_price_cent,stock_mode)
        VALUES (#{tenantId},#{spuId},#{skuCode},#{skuName},#{salePriceCent},#{stockMode})
        """)
    void insertSku(GoodsSkuPO row);

    /**
     * 按租户和编号读取商品主数据。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @return 匹配的商品行
     */
    @Select("SELECT * FROM goods_spu WHERE tenant_id=#{tenant} AND spu_code=#{code} AND deleted=0")
    GoodsSpuPO goods(String tenant, String code);

    /**
     * 读取指定商品的规格。
     *
     * @param tenant 租户编号
     * @param spuId 商品主键
     * @return 规格列表
     */
    @Select("SELECT * FROM goods_sku WHERE tenant_id=#{tenant} AND spu_id=#{spuId} AND deleted=0 ORDER BY id")
    List<GoodsSkuPO> skus(String tenant, long spuId);

    /**
     * 查询门店可售目录规格和成交依据。
     *
     * @param tenant 租户编号
     * @param shopId 门店主键
     * @param skuCode 租户内规格编号
     * @return 可售规格报价
     */
    GoodsSkuOffer offer(String tenant, long shopId, String skuCode);

    /**
     * 插入订单。
     *
     * @param row 映射到业务表的数据行
     */
    @Insert("""
        INSERT INTO trade_order (tenant_id,order_no,shop_id,order_kind,fulfillment_mode,order_state,
            fulfillment_state,refund_state,placed_time,gross_amount_cent)
        VALUES (#{tenantId},#{orderNo},#{shopId},#{orderKind},#{fulfillmentMode},#{orderState},
            #{fulfillmentState},#{refundState},UTC_TIMESTAMP(3),#{grossAmountCent})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertOrder(TradeOrderPO row);

    /**
     * 插入订单明细快照。
     *
     * @param row 映射到业务表的数据行
     */
    @Insert("""
        INSERT INTO trade_order_item (tenant_id,order_id,line_no,sku_id,product_name_snapshot,sku_name_snapshot,quantity,unit_price_cent)
        VALUES (#{tenantId},#{orderId},#{lineNo},#{skuId},#{productNameSnapshot},#{skuNameSnapshot},#{quantity},#{unitPriceCent})
        """)
    void insertItem(TradeOrderItemPO row);

    /**
     * 按租户和编号读取订单。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @return 匹配的订单行
     */
    @Select("SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND order_no=#{code} AND deleted=0")
    TradeOrderPO order(String tenant, String code);

    /**
     * 锁定指定订单行以执行状态修改。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @return 锁定的订单行
     */
    @Select("SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND order_no=#{orderNo} AND deleted=0 FOR UPDATE")
    TradeOrderPO lockOrder(String tenant, String orderNo);

    /**
     * 持久化订单状态变更。
     *
     * @param row 映射到业务表的数据行
     * @return 更新的行数
     */
    @Update("""
        UPDATE trade_order
        SET order_state = #{orderState}, fulfillment_state = #{fulfillmentState}, refund_state = #{refundState},
            paid_amount_cent = #{paidAmountCent}, paid_time = #{paidTime}
        WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
        """)
    int updateTradeState(TradeOrderPO row);

    /**
     * 读取指定订单的成交明细。
     *
     * @param tenant 租户编号
     * @param orderId 订单主键
     * @return 明细行列表
     */
    @Select("SELECT * FROM trade_order_item WHERE tenant_id=#{tenant} AND order_id=#{orderId} AND deleted=0 ORDER BY line_no")
    List<TradeOrderItemPO> items(String tenant, long orderId);
    /**
     * 按租户和主键读取商品。
     *
     * @param tenant 租户编号
     * @param id 业务表主键
     * @return 商品行
     */
    @Select("SELECT * FROM goods_spu WHERE tenant_id=#{tenant} AND id=#{id} AND deleted=0")
    GoodsSpuPO goodsById(String tenant, Long id);

    /**
     * 按租户和主键读取订单。
     *
     * @param tenant 租户编号
     * @param id 业务表主键
     * @return 订单行
     */
    @Select("SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND id=#{id} AND deleted=0")
    TradeOrderPO orderById(String tenant, Long id);

}
