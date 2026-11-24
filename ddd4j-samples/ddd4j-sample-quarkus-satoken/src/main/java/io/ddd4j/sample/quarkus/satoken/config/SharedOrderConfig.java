package io.ddd4j.sample.quarkus.satoken.config;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
/** 原生 CDI 组装共享订单；此示例存储仅为进程内演示。 */
@ApplicationScoped
public class SharedOrderConfig {
    /** @return 同一组共享订单存储与事务端口 */
    @Produces @Singleton public InMemoryOrderAdapters orders() { return new InMemoryOrderAdapters(); }
    /**
     * @param store 同一共享存储
     * @return 共享订单应用服务
     */
    @Produces @Singleton public OrderApplicationService service(InMemoryOrderAdapters store) {
        return new OrderApplicationService(store, store, store, store, store);
    }
}
