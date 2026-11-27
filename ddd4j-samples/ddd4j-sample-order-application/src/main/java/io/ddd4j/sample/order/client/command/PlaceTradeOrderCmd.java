package io.ddd4j.sample.order.client.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.cqrs.command.Command;
import lombok.Data;

import java.util.List;

/**
 * 按选定的规格与购买数量下单。
 */
@Data
public class PlaceTradeOrderCmd implements Command {
    /**
     * 由 HTTP 路径提供的订单所属租户。
     */
    @JsonIgnore
    private String tenantId;
    /**
     * 由 HTTP 路径提供的租户内订单号。
     */
    @JsonIgnore
    private String orderNo;
    /**
     * 交易或商品适用的门店标识。
     */
    private Long shopId;
    /**
     * 本次下单请求的规格与购买件数。
     */
    private List<TradeOrderItemCmd> items;
}
