package io.ddd4j.sample.helidon.cqrs;

import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.CatalogCqrsResource;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.ws.rs.Path;

import java.util.List;

/**
 * Helidon 商品交易 HTTP 入口，接入示例的命令总线及订单投影。
 */
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogCqrsResource {
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
    CatalogTradeResource(CatalogApi api) {
        super(new CatalogCqrs(api, new DefaultCommandBus(List.of(CatalogCqrs.executor(api)))));
    }
}
