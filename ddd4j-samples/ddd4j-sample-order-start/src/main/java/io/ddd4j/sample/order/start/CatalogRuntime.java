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
    private static final DomainEventPublisher FRAMEWORK_EVENTS = new DomainEventPublisher() {
        @Override
        public <ID extends EntityId> void publish(DomainEvent<ID> event) {
            event.publish();
        }
    };

    private CatalogRuntime() {
    }

    public static CatalogApi start() {
        return start(FRAMEWORK_EVENTS);
    }

    public static CatalogApi start(DomainEventPublisher publisher) {
        return start(required("CATALOG_JDBC_URL"), required("CATALOG_DB_USER"), required("CATALOG_DB_PASSWORD"), publisher);
    }

    public static CatalogApi start(String url, String username, String password) {
        return start(url, username, password, FRAMEWORK_EVENTS);
    }

    public static CatalogApi start(String url, String username, String password, DomainEventPublisher publisher) {
        return assemble(CatalogDatabase.open(url, username, password), publisher);
    }

    public static CatalogApi start(DataSource source) {
        return start(source, FRAMEWORK_EVENTS);
    }

    public static CatalogApi start(DataSource source, DomainEventPublisher publisher) {
        return assemble(CatalogDatabase.open(source), publisher);
    }

    private static CatalogApi assemble(TradeSession.Transactions transactions, DomainEventPublisher publisher) {
        var service = new CatalogService(transactions, Objects.requireNonNull(publisher), Clock.systemUTC());
        return new CatalogApi(service);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (StrKit.isBlank(value)) {
            throw new IllegalStateException("缺少环境变量 " + key);
        }
        return value;
    }
}
