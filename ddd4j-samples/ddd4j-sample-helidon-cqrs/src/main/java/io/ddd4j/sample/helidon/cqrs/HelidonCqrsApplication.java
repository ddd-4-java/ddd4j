package io.ddd4j.sample.helidon.cqrs;

import io.ddd4j.sample.helidon.cqrs.cqrs.CommandBus;
import io.ddd4j.sample.helidon.cqrs.cqrs.InMemoryEventStore;
import io.ddd4j.sample.helidon.cqrs.cqrs.ViewManager;
import io.ddd4j.sample.helidon.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.helidon.cqrs.command.CreateOrderCommandHandler;
import io.ddd4j.sample.helidon.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.helidon.cqrs.repository.EventSourcingOrderRepository;
import io.ddd4j.sample.helidon.cqrs.web.OrderResource;
import io.helidon.microprofile.server.Server;

/**
 * Helidon MP CQRS 集成示例启动入口。
 */
public class HelidonCqrsApplication {

    // 共享组件（手动装配）
    public static final InMemoryEventStore EVENT_STORE = new InMemoryEventStore();
    public static final EventSourcingOrderRepository ORDER_REPO = new EventSourcingOrderRepository(EVENT_STORE);
    public static final CreateOrderCommandHandler COMMAND_HANDLER = new CreateOrderCommandHandler(ORDER_REPO);
    public static final CommandBus COMMAND_BUS = createCommandBus();
    public static final OrderSummaryView READ_VIEW = new OrderSummaryView(ORDER_REPO);
    public static final ViewManager VIEW_MANAGER = createViewManager();

    private static CommandBus createCommandBus() {
        CommandBus bus = new CommandBus();
        bus.register(CreateOrderCommand.class, COMMAND_HANDLER::execute);
        return bus;
    }

    private static ViewManager createViewManager() {
        ViewManager mgr = new ViewManager(EVENT_STORE);
        mgr.register(READ_VIEW);
        return mgr;
    }

    public static void main(String[] args) {
        Server.builder()
                .addApplication(new HelidonCqrsJaxRsApplication())
                .build()
                .start();
        VIEW_MANAGER.start();
    }
}
