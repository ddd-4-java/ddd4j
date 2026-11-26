package io.ddd4j.sample.order.client.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.cqrs.command.Command;
import lombok.Data;

/** 上架一个指定门店可售的不限量套餐及其规格。 */
@Data
public class CreateGoodsSpuCmd implements Command {
    @JsonIgnore
    private String tenantId;
    @JsonIgnore
    private String spuCode;
    private String spuName;
    private String skuCode;
    private String skuName;
    private Long salePriceCent;
    private Long shopId;
}
