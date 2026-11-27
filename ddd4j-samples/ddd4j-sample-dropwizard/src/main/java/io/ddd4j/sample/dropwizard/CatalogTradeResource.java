package io.ddd4j.sample.dropwizard;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.ws.rs.Path;

/**
 * Dropwizard 商品交易 HTTP 入口，转发商品和订单请求到共用的交易用例。
 */
@Path("/api")
public class CatalogTradeResource extends CatalogResource {
    /**
     * 使用示例的默认运行配置组装商品交易资源。
     */
    public CatalogTradeResource() {
        this(CatalogRuntime.start());
    }

    /**
     * 将传入的商品交易操作绑定到 HTTP 入口。
     *
     * @param api 共用的商品交易 HTTP 操作
     */
    public CatalogTradeResource(CatalogApi api) {
        super(api);
    }
}
