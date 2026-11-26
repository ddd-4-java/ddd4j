package io.ddd4j.sample.order.client.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.cqrs.command.Command;
import lombok.Data;
import java.util.List;

/** 按选定的规格与购买数量下单。 */
@Data
public class PlaceTradeOrderCmd implements Command {
    @JsonIgnore
    private String tenantId;
    @JsonIgnore
    private String orderNo;
    private Long shopId;
    private List<TradeOrderItemCmd> items;
}
