package io.ddd4j.sample.order.app.projection;

import lombok.Value;

/** 查询已经构建的交易视图。 */
@Value
public class ProjectedTradeOrderQuery {
    /** \u4e1a\u52a1\u6240\u5c5e\u79df\u6237\u6807\u8bc6\u3002 */
    String tenantId;
    /** \u79df\u6237\u5185\u552f\u4e00\u7684\u4e1a\u52a1\u8ba2\u5355\u53f7\u3002 */
    String orderNo;
}
