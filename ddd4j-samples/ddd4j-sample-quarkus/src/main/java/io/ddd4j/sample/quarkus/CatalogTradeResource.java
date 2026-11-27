package io.ddd4j.sample.quarkus;

import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Quarkus 商品交易 HTTP 入口，转发商品和订单请求到共用的交易用例。
 */
@io.quarkus.runtime.Startup
@jakarta.inject.Singleton
@io.quarkus.arc.properties.IfBuildProperty(name = "sample.goods.standalone", stringValue = "false", enableIfMissing = true)
@Path("/api")
public class CatalogTradeResource extends CatalogResource {
    /**
     * 按运行框架提供的连接配置初始化商品交易 HTTP 入口。
     *
     * @param url       商品交易数据库的 JDBC 地址
     * @param username  数据库账号
     * @param password  数据库账号密码
     * @param publisher 商品交易领域事件发布器
     */
    @Inject
    public CatalogTradeResource(@ConfigProperty(name = "catalog.jdbc.url") String url,
                                @ConfigProperty(name = "catalog.db.user") String username,
                                @ConfigProperty(name = "catalog.db.password") String password,
                                DomainEventPublisher publisher) {
        super(CatalogRuntime.start(url, username, password, publisher));
    }
}
