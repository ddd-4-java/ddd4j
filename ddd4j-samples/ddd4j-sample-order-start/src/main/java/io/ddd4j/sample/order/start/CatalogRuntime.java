package io.ddd4j.sample.order.start;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.domain.gateway.TradeSession;
import io.ddd4j.sample.order.infrastructure.CatalogDatabase;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Objects;

/** 组装商品交易 HTTP 适配器、应用服务和四表持久化。 */
public final class CatalogRuntime {
    /** \u5c06\u5546\u54c1\u4ea4\u6613\u9886\u57df\u4e8b\u4ef6\u8f6c\u53d1\u7ed9\u8fd0\u884c\u6846\u67b6\u7684\u53d1\u5e03\u5668\u3002 */
    private static final DomainEventPublisher FRAMEWORK_EVENTS = new DomainEventPublisher() {
        @Override
        public <ID extends EntityId> void publish(DomainEvent<ID> event) {
            event.publish();
        }
    };

    /** \u9650\u5236\u5de5\u5177\u7c7b\u5b9e\u4f8b\u5316\u3002 */
    private CatalogRuntime() {
    }

    /**
     * 读取环境中的数据库配置并装配商品交易入口。
     *
     * @return 商品交易入口
     */
    public static CatalogApi start() {
        return start(FRAMEWORK_EVENTS);
    }

    /**
     * 读取环境中的数据库配置并装配商品交易入口。
     *
     * @param publisher 框架原生领域事件发布器
     * @return 商品交易入口
     */
    public static CatalogApi start(DomainEventPublisher publisher) {
        return start(required("CATALOG_JDBC_URL"), required("CATALOG_DB_USER"), required("CATALOG_DB_PASSWORD"), publisher);
    }

    /**
     * 初始化商品交易四表并装配示例入口。
     *
     * @param url 四表所在的 MySQL JDBC URL
     * @param username 数据库用户名
     * @param password 数据库密码
     * @return 商品交易入口
     */
    public static CatalogApi start(String url, String username, String password) {
        return start(url, username, password, FRAMEWORK_EVENTS);
    }

    /**
     * 初始化商品交易四表并装配示例入口。
     *
     * @param url 四表所在的 MySQL JDBC URL
     * @param username 数据库用户名
     * @param password 数据库密码
     * @param publisher 框架原生领域事件发布器
     * @return 商品交易入口
     */
    public static CatalogApi start(String url, String username, String password, DomainEventPublisher publisher) {
        return assemble(CatalogDatabase.open(url, username, password), publisher);
    }

    /**
     * 初始化商品交易四表并装配示例入口。
     *
     * @param source 用于初始化和事务的数据源
     * @return 商品交易入口
     */
    public static CatalogApi start(DataSource source) {
        return start(source, FRAMEWORK_EVENTS);
    }

    /**
     * 初始化商品交易四表并装配示例入口。
     *
     * @param source 用于初始化和事务的数据源
     * @param publisher 框架原生领域事件发布器
     * @return 商品交易入口
     */
    public static CatalogApi start(DataSource source, DomainEventPublisher publisher) {
        return assemble(CatalogDatabase.open(source), publisher);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u4e8b\u52a1\u5165\u53e3\u3001\u53d1\u5e03\u5668\u548c UTC \u65f6\u949f\u7ec4\u88c5\u5546\u54c1\u4ea4\u6613\u7528\u4f8b\u3002
     * @param transactions \u5546\u54c1\u4ea4\u6613\u7684\u4e8b\u52a1\u5165\u53e3
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @return \u4f9b\u5404\u8fd0\u884c\u6846\u67b6\u590d\u7528\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c
     */
    private static CatalogApi assemble(TradeSession.Transactions transactions, DomainEventPublisher publisher) {
        var service = new CatalogService(transactions, Objects.requireNonNull(publisher), Clock.systemUTC());
        return new CatalogApi(service);
    }

    /**
     * \u8bfb\u53d6\u4e00\u4e2a\u5fc5\u586b\u7684\u8fd0\u884c\u73af\u5883\u53d8\u91cf\u3002
     * @param key \u73af\u5883\u53d8\u91cf\u540d
     * @return \u914d\u7f6e\u7684\u975e\u7a7a\u767d\u53d8\u91cf\u503c
     * @throws IllegalStateException \u73af\u5883\u53d8\u91cf\u6ca1\u6709\u914d\u7f6e\u6216\u53ea\u5305\u542b\u7a7a\u767d
     */
    private static String required(String key) {
        String value = System.getenv(key);
        if (StrKit.isBlank(value)) {
            throw new IllegalStateException("缺少环境变量 " + key);
        }
        return value;
    }
}
