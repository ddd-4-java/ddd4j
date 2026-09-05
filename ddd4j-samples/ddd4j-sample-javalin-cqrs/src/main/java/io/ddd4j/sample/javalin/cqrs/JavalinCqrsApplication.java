package io.ddd4j.sample.javalin.cqrs;

import java.util.Objects;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
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
import io.ddd4j.sample.javalin.cqrs.spi.AnonymousSubjectProvider;
import io.ddd4j.sample.javalin.cqrs.spi.DefaultI18nProvider;
import io.ddd4j.sample.javalin.cqrs.spi.NoOpDomainEventPublisher;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

/**
 * ddd4j Javalin 平台的 Order/Goods CQRS 启动入口。
 *
 * <h3>核心要点</h3>
 * <p>Javalin 无 DI 容器，业务方在 main 中手动完成：
 * <ol>
 *   <li>注入 4 个核心 SPI（{@link BaseContext#inject}）</li>
 *   <li>注册仓储（{@link RepositoryRegistry#register}）</li>
 *   <li>初始化 {@link CacheKit} 缓存域（CQRS 缓存增强）</li>
 *   <li>构造应用服务、缓存服务、命令/查询控制器</li>
 *   <li>注册事件订阅（{@link OrderEventListener}）</li>
 *   <li>启动 Javalin，注入路由</li>
 * </ol>
 *
 * <h3>REST 端点（CQRS）</h3>
 * <ul>
 *   <li>POST /api/orders, POST /api/orders/{id}/lines, /pay, /ship, /cancel — Order 命令端</li>
 *   <li>GET  /api/orders/query/list, /stats, /buyer/{buyerId}/count, /detail/{id} — Order 查询端（缓存）</li>
 *   <li>POST/PUT/DELETE /api/goods — Goods 命令端</li>
 *   <li>GET  /api/goods/query/by-id/{id}, /by-code, /list, /by-status, /count, /cache-stats — Goods 查询端（缓存）</li>
 * </ul>
 *
 * <h3>运行</h3>
 * <pre>{@code
 * mvn -pl ddd4j-samples/ddd4j-sample-javalin-cqrs exec:java
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class JavalinCqrsApplication {

    /**
     * 默认 HTTP 端口
     */
    public static final int DEFAULT_PORT = 7001;

    private JavalinCqrsApplication() {
    }

    /**
     * 启动 Javalin 应用。
     *
     * @param args 启动参数（第一个参数为 HTTP 端口，默认 7001）
     */
    public static void main(String[] args) {
        int port = parsePort(args);

        // 1. 注入核心 SPI
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, new NoOpDomainEventPublisher());
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, new AnonymousSubjectProvider());
        BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, new DefaultI18nProvider());

        // 2. 注册仓储
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RepositoryRegistry.register(Order.class, orderRepository);
        InMemoryGoodsRepository goodsRepository = new InMemoryGoodsRepository();
        RepositoryRegistry.register(Goods.class, goodsRepository);

        // 3. 初始化 CacheKit 缓存域（CQRS 读侧缓存）
        initCaches();

        // 4. 构造应用服务 + 缓存服务
        OrderApplicationService orderService = new OrderApplicationService(orderRepository);
        GoodsApplicationService goodsService = new GoodsApplicationService(goodsRepository);
        OrderCacheService orderCacheService = new OrderCacheService(orderRepository);
        GoodsCacheService goodsCacheService = new GoodsCacheService(goodsRepository);

        // 5. 注册订单事件监听器
        OrderEventListener orderEventListener = new OrderEventListener();
        io.ddd4j.core.cqrs.readmodel.TypedEventDispatcher dispatcher =
                new io.ddd4j.core.cqrs.readmodel.TypedEventDispatcher(orderEventListener.handlers());

        // 6. 构造命令/查询控制器（CQRS 分离）
        OrderController orderController = new OrderController(orderService);
        OrderCQRSQueryController orderQueryController = new OrderCQRSQueryController(orderCacheService);
        GoodsController goodsController = new GoodsController(goodsService);
        GoodsReadController goodsReadController = new GoodsReadController(goodsCacheService, goodsRepository);

        // 7. 启动 Javalin
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.routes.apiBuilder(() -> {
                orderController.routes();
                orderQueryController.routes();
                goodsController.routes();
                goodsReadController.routes();
            });
        });

        DomainEventPublisher publisher = Contexts.getOrThrow(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        log.info("[Bootstrap] DomainEventPublisher ready: {}", publisher.getClass().getSimpleName());

        app.start(port);
        log.info("Javalin (CQRS) started on http://localhost:{}", port);
    }

    /**
     * 初始化 CacheKit 缓存域（CQRS 读侧缓存）。
     */
    private static void initCaches() {
        // Order 缓存域
        CacheKit.build(OrderCacheService.BIZ_ORDER_STATS,
                builder -> builder.maximumSize(10000).expireAfterWriteSeconds(300).initialCapacity(128).recordStats(true));
        CacheKit.build(OrderCacheService.BIZ_BUYER_ORDER_COUNT,
                builder -> builder.maximumSize(10000).expireAfterWriteSeconds(3600).initialCapacity(256).recordStats(true));
        CacheKit.build(OrderCacheService.BIZ_ORDER_DETAIL,
                builder -> builder.maximumSize(10000).expireAfterWriteSeconds(300).initialCapacity(256).recordStats(true));
        // Goods 缓存域
        CacheKit.build(GoodsCacheService.BIZ_GOODS_DETAIL,
                builder -> builder.maximumSize(20000).expireAfterWriteSeconds(600).initialCapacity(256).recordStats(true));
        CacheKit.build(GoodsCacheService.BIZ_GOODS_LIST,
                builder -> builder.maximumSize(2000).expireAfterWriteSeconds(120).initialCapacity(128).recordStats(true));
        log.info("[Bootstrap] CacheKit initialized with {} domains: {}",
                CacheKit.getCacheNames().size(), CacheKit.getCacheNames());
    }

    private static int parsePort(String[] args) {
        if (Objects.isNull(args) || args.length == 0) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            log.warn("[Bootstrap] invalid port arg, fallback to {}", DEFAULT_PORT, exception);
            return DEFAULT_PORT;
        }
    }
}
