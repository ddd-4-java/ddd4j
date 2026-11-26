package io.ddd4j.sample.order.adapter.http;

/** 商品交易 HTTP 适配器使用的四个用例。 */
public interface CatalogEndpoints {
    Reply createGoods(String tenant, String code, String body);
    Reply goods(String tenant, String code);
    Reply placeOrder(String tenant, String code, String body);
    Reply confirmPayment(String tenant, String orderNo, String body);
    Reply cancelOrder(String tenant, String orderNo);
    Reply fulfillOrder(String tenant, String orderNo);
    Reply order(String tenant, String code);
}
