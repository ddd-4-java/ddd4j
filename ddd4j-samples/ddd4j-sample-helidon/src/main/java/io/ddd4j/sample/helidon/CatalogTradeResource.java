package io.ddd4j.sample.helidon;

import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.adapter.http.CatalogResource;
import jakarta.ws.rs.Path;

/**
 * Helidon 商品交易 HTTP 入口，转发商品和订单请求到共用的交易用例。
 */
@Path("/api")
public class CatalogTradeResource extends CatalogResource {
    /**
     * 将传入的商品交易操作绑定到 HTTP 入口。
     *
     * @param catalog 已装配的商品交易 HTTP 操作
     */
    public CatalogTradeResource(CatalogEndpoints catalog) {
        super(catalog);
    }
}
