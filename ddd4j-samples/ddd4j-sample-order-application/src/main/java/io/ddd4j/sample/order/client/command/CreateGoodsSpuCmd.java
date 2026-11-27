package io.ddd4j.sample.order.client.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.cqrs.command.Command;
import lombok.Data;

/** 上架一个指定门店可售的不限量套餐及其规格。 */
@Data
public class CreateGoodsSpuCmd implements Command {
    /** \u7531 HTTP \u8def\u5f84\u63d0\u4f9b\u7684\u5546\u54c1\u6240\u5c5e\u79df\u6237\u3002 */
    @JsonIgnore
    private String tenantId;
    /** \u7531 HTTP \u8def\u5f84\u63d0\u4f9b\u7684\u79df\u6237\u5185\u5546\u54c1\u7f16\u7801\u3002 */
    @JsonIgnore
    private String spuCode;
    /** \u5546\u54c1\u5c55\u793a\u540d\u79f0\u3002 */
    private String spuName;
    /** \u79df\u6237\u5185\u7684\u89c4\u683c\u7f16\u7801\u3002 */
    private String skuCode;
    /** \u5546\u54c1\u89c4\u683c\u7684\u5c55\u793a\u540d\u79f0\u3002 */
    private String skuName;
    /** \u5546\u54c1\u89c4\u683c\u7684\u9500\u552e\u4ef7\uff0c\u5355\u4f4d\u4e3a\u4eba\u6c11\u5e01\u5206\u3002 */
    private Long salePriceCent;
    /** \u4ea4\u6613\u6216\u5546\u54c1\u9002\u7528\u7684\u95e8\u5e97\u6807\u8bc6\u3002 */
    private Long shopId;
}
