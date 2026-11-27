package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.data.cqrs.dropwizard.DropwizardCommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrsResource;
import io.dropwizard.core.Application;
import jakarta.ws.rs.Path;
import java.util.List;

/** Dropwizard \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u63a5\u5165\u793a\u4f8b\u7684\u547d\u4ee4\u603b\u7ebf\u53ca\u8ba2\u5355\u6295\u5f71\u3002 */
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogCqrsResource {
    /**
     * 初始化 <code>CatalogTradeResource</code> 并委托其默认构造路径。
     *
     * @param app 传入此操作的 <code>Application&lt;?&gt;</code> 类型 <code>app</code>
     * @param api 传入此操作的 <code>CatalogApi</code> 类型 <code>api</code>
     */
    public CatalogTradeResource(Application<?> app, CatalogApi api) {
        super(new CatalogCqrs(api, DropwizardCommandBus.create(app, List.of(CatalogCqrs.executor(api)))));
    }
}
