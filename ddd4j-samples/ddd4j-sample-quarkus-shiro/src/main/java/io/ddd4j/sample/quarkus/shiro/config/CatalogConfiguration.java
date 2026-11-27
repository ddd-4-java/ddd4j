package io.ddd4j.sample.quarkus.shiro.config;

import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * 从 Quarkus 配置读取数据库连接，注册共用的商品交易入口。
 */
@ApplicationScoped
public class CatalogConfiguration {
    /**
     * 从数据库配置及事件发布器装配共用商品交易入口。
     *
     * @param url       商品交易示例数据库 JDBC 地址
     * @param username  数据库登录账号
     * @param password  数据库连接口令
     * @param publisher 当前运行时用于转发交易事件的发布器
     * @return 供框架 HTTP 资源调用的商品交易操作
     */
    @Produces
    @ApplicationScoped
    CatalogApi catalog(@ConfigProperty(name = "catalog.jdbc.url") String url,
                       @ConfigProperty(name = "catalog.db.user") String username,
                       @ConfigProperty(name = "catalog.db.password") String password,
                       DomainEventPublisher publisher) {
        return CatalogRuntime.start(url, username, password, publisher);
    }
}
