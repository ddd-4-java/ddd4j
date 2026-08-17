package io.ddd4j.sample.javalin.cqrs;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.javalin.cqrs.cache.GoodsCacheService;
import io.ddd4j.sample.javalin.cqrs.cache.OrderCacheService;
import io.ddd4j.sample.javalin.cqrs.event.OrderEventListener;
import io.ddd4j.sample.javalin.cqrs.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.cqrs.goods.domain.Goods;
import io.ddd4j.sample.javalin.cqrs.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.cqrs.goods.web.GoodsController;
import io.ddd4j.sample.javalin.cqrs.goods.web.GoodsReadController;
import io.ddd4j.sample.javalin.cqrs.order.application.OrderApplicationService;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Order;
import io.ddd4j.sample.javalin.cqrs.order.infrastructure.InMemoryOrderRepository;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderCQRSQueryController;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderController;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson3;

import java.util.Collection;

/**
 * 测试基础设施：手动构造 Javalin 应用 + CacheKit + SPI 注入。
 *
 * <p>本 sample 是无 auth 的纯 CQRS 示例，所以测试不需要处理 token。
 */
public final class TestSupport {

    private TestSupport() {
    }

    public static Javalin start() {
        // 1. SPI
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, new NoopDomainEventPublisher());
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, new NoopSubjectProvider());
        BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, new NoopI18nProvider());

        // 2. 仓储
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RepositoryRegistry.register(Order.class, orderRepository);
        InMemoryGoodsRepository goodsRepository = new InMemoryGoodsRepository();
        RepositoryRegistry.register(Goods.class, goodsRepository);

        // 3. CacheKit
        CacheKit.build(OrderCacheService.BIZ_ORDER_STATS,
                b -> b.maximumSize(10000).expireAfterWriteSeconds(300).initialCapacity(128).recordStats(true));
        CacheKit.build(OrderCacheService.BIZ_BUYER_ORDER_COUNT,
                b -> b.maximumSize(10000).expireAfterWriteSeconds(3600).initialCapacity(256).recordStats(true));
        CacheKit.build(OrderCacheService.BIZ_ORDER_DETAIL,
                b -> b.maximumSize(10000).expireAfterWriteSeconds(300).initialCapacity(256).recordStats(true));
        CacheKit.build(GoodsCacheService.BIZ_GOODS_DETAIL,
                b -> b.maximumSize(20000).expireAfterWriteSeconds(600).initialCapacity(256).recordStats(true));
        CacheKit.build(GoodsCacheService.BIZ_GOODS_LIST,
                b -> b.maximumSize(2000).expireAfterWriteSeconds(120).initialCapacity(128).recordStats(true));

        // 4. 应用服务 + 缓存服务
        OrderApplicationService orderService = new OrderApplicationService(orderRepository);
        GoodsApplicationService goodsService = new GoodsApplicationService(goodsRepository);
        OrderCacheService orderCacheService = new OrderCacheService(orderRepository);
        GoodsCacheService goodsCacheService = new GoodsCacheService(goodsRepository);

        // 5. 事件监听器
        OrderEventListener orderEventListener = new OrderEventListener();
        @SuppressWarnings("unused")
        io.ddd4j.core.cqrs.readmodel.TypedEventDispatcher dispatcher =
                new io.ddd4j.core.cqrs.readmodel.TypedEventDispatcher(orderEventListener.handlers());

        // 6. 控制器
        OrderController orderController = new OrderController(orderService);
        OrderCQRSQueryController orderQueryController = new OrderCQRSQueryController(orderCacheService);
        GoodsController goodsController = new GoodsController(goodsService);
        GoodsReadController goodsReadController = new GoodsReadController(goodsCacheService, goodsRepository);

        Javalin app = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.jsonMapper(new JavalinJackson3());
            cfg.routes.apiBuilder(() -> {
                orderController.routes();
                orderQueryController.routes();
                goodsController.routes();
                goodsReadController.routes();
            });
        });
        app.start(0);
        return app;
    }

    private static final class NoopDomainEventPublisher implements DomainEventPublisher {
        @Override
        public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        }

        @Override
        public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> events) {
        }
    }

    private static final class NoopI18nProvider implements I18nProvider {
        @Override
        public String getMessage(String key, Object... args) {
            return key;
        }
    }

    private static final class NoopSubjectProvider implements SubjectProvider {
        @Override
        public io.ddd4j.core.subject.Subject getSubject() {
            return null;
        }
    }
}
