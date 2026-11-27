package io.ddd4j.sample.quarkus.shiro;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

/**
 * Quarkus 商品交易 HTTP 入口，转发商品和订单请求到共用的交易用例。
 */
@Startup
@Singleton
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogResource {
    /**
     * 将传入的商品交易操作绑定到 HTTP 入口。
     *
     * @param catalog 已装配的商品交易 HTTP 操作
     */
    @Inject
    public CatalogTradeResource(CatalogApi catalog) {
        super(catalog);
    }
}
