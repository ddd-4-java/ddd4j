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

public interface CatalogMapper {
    @Insert("INSERT INTO goods_spu (tenant_id,spu_code,spu_name,product_kind,applicable_shop_ids,review_state,is_visible,is_on_sale) " +
                    "VALUES (#{tenantId},#{spuCode},#{spuName},#{productKind},#{applicableShopIds},#{reviewState},#{isVisible},#{isOnSale}) ")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertGoods(GoodsSpuPO row);

    @Insert("INSERT INTO goods_sku (tenant_id,spu_id,sku_code,sku_name,sale_price_cent,stock_mode) " +
                    "VALUES (#{tenantId},#{spuId},#{skuCode},#{skuName},#{salePriceCent},#{stockMode}) ")
    void insertSku(GoodsSkuPO row);

    @Select("SELECT * FROM goods_spu WHERE tenant_id=#{tenant} AND spu_code=#{code} AND deleted=0")
    GoodsSpuPO goods(String tenant, String code);

    @Select("SELECT * FROM goods_sku WHERE tenant_id=#{tenant} AND spu_id=#{spuId} AND deleted=0 ORDER BY id")
    List<GoodsSkuPO> skus(String tenant, long spuId);

    GoodsSkuOffer offer(String tenant, long shopId, String skuCode);

    @Insert("INSERT INTO trade_order (tenant_id,order_no,shop_id,order_kind,fulfillment_mode,order_state, " +
                    "    fulfillment_state,refund_state,placed_time,gross_amount_cent) " +
                    "VALUES (#{tenantId},#{orderNo},#{shopId},#{orderKind},#{fulfillmentMode},#{orderState}, " +
                    "    #{fulfillmentState},#{refundState},UTC_TIMESTAMP(3),#{grossAmountCent}) ")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertOrder(TradeOrderPO row);

    @Insert("INSERT INTO trade_order_item (tenant_id,order_id,line_no,sku_id,product_name_snapshot,sku_name_snapshot,quantity,unit_price_cent) " +
                    "VALUES (#{tenantId},#{orderId},#{lineNo},#{skuId},#{productNameSnapshot},#{skuNameSnapshot},#{quantity},#{unitPriceCent}) ")
    void insertItem(TradeOrderItemPO row);

    @Select("SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND order_no=#{code} AND deleted=0")
    TradeOrderPO order(String tenant, String code);

    @Select("SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND order_no=#{orderNo} AND deleted=0 FOR UPDATE")
    TradeOrderPO lockOrder(String tenant, String orderNo);

    @Update("UPDATE trade_order " +
                    "SET order_state = #{orderState}, fulfillment_state = #{fulfillmentState}, refund_state = #{refundState}, " +
                    "    paid_amount_cent = #{paidAmountCent}, paid_time = #{paidTime} " +
                    "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0 ")
    int updateTradeState(TradeOrderPO row);

    @Select("SELECT * FROM trade_order_item WHERE tenant_id=#{tenant} AND order_id=#{orderId} AND deleted=0 ORDER BY line_no")
    List<TradeOrderItemPO> items(String tenant, long orderId);
    @Select("SELECT * FROM goods_spu WHERE tenant_id=#{tenant} AND id=#{id} AND deleted=0")
    GoodsSpuPO goodsById(String tenant, Long id);

    @Select("SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND id=#{id} AND deleted=0")
    TradeOrderPO orderById(String tenant, Long id);

}
