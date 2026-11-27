package io.ddd4j.sample.order.adapter.http;

/**
 * 商品交易 HTTP 适配器使用的四个用例。
 */
public interface CatalogEndpoints {
    /**
     * 创建商品和初始规格。
     *
     * @param tenant 租户编号
     * @param code   租户内业务编号
     * @param body   请求 JSON 正文
     * @return 业务操作响应
     */
    Reply createGoods(String tenant, String code, String body);

    /**
     * 查询指定商品。
     *
     * @param tenant 租户编号
     * @param code   租户内业务编号
     * @return 业务操作响应
     */
    Reply goods(String tenant, String code);

    /**
     * 创建交易订单。
     *
     * @param tenant 租户编号
     * @param code   租户内业务编号
     * @param body   请求 JSON 正文
     * @return 业务操作响应
     */
    Reply placeOrder(String tenant, String code, String body);

    /**
     * 确认订单支付。
     *
     * @param tenant  租户编号
     * @param orderNo 租户内订单编号
     * @param body    请求 JSON 正文
     * @return 业务操作响应
     */
    Reply confirmPayment(String tenant, String orderNo, String body);

    /**
     * 取消指定订单。
     *
     * @param tenant  租户编号
     * @param orderNo 租户内订单编号
     * @return 业务操作响应
     */
    Reply cancelOrder(String tenant, String orderNo);

    /**
     * 完成指定订单的履约。
     *
     * @param tenant  租户编号
     * @param orderNo 租户内订单编号
     * @return 业务操作响应
     */
    Reply fulfillOrder(String tenant, String orderNo);

    /**
     * 查询指定订单。
     *
     * @param tenant 租户编号
     * @param code   租户内业务编号
     * @return 业务操作响应
     */
    Reply order(String tenant, String code);
}
