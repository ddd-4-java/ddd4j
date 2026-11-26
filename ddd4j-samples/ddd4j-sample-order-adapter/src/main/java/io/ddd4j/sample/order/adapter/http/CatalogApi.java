package io.ddd4j.sample.order.adapter.http;

import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.app.projection.CatalogOrderView;
import io.ddd4j.sample.order.app.projection.ProjectedTradeOrderQuery;
import io.ddd4j.sample.order.client.command.ConfirmTradePaymentCmd;
import io.ddd4j.sample.order.client.command.ChangeTradeOrderCmd;
import io.ddd4j.sample.order.domain.model.TradeOrderStateConflict;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;
import io.ddd4j.sample.order.client.query.GoodsSpuQuery;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.query.QueryBus;
import io.ddd4j.core.cqrs.query.DefaultQueryBus;
import io.ddd4j.sample.order.app.executor.CatalogCommands;
import io.ddd4j.sample.order.app.executor.GoodsSpuQueryHandler;
import io.ddd4j.sample.order.app.executor.TradeOrderQueryHandler;
import java.util.List;
import io.ddd4j.sample.order.domain.gateway.DuplicateTradeKey;

public class CatalogApi implements CatalogEndpoints {
    private final CatalogCommands executor;
    private final CommandBus commands;
    private final QueryBus queries;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public CatalogApi(CatalogService service) {
        executor = new CatalogCommands(service);
        commands = new DefaultCommandBus(List.of(executor));
        queries = new DefaultQueryBus(List.of(new GoodsSpuQueryHandler(service), new TradeOrderQueryHandler(service)));
    }

    public CatalogCommands executor() {
        return executor;
    }

    public Reply createGoods(String tenant, String code, String body) {
        return createGoods(commands, tenant, code, body);
    }

    public Reply createGoods(String tenant, String code, CreateGoodsSpuCmd command) {
        return invoke(201, () -> create(commands, tenant, code, command));
    }

    Reply createGoods(CommandBus bus, String tenant, String code, String body) {
        return invoke(201, () -> create(bus, tenant, code, json.readValue(body, CreateGoodsSpuCmd.class)));
    }

    private static Object create(CommandBus bus, String tenant, String code, CreateGoodsSpuCmd command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("商品命令不能为空");
        }
        command.setTenantId(tenant);
        command.setSpuCode(code);
        return bus.execute(command).data().orElseThrow();
    }

    public Reply placeOrder(String tenant, String code, String body) {
        return placeOrder(commands, tenant, code, body);
    }

    public Reply placeOrder(String tenant, String code, PlaceTradeOrderCmd command) {
        return invoke(201, () -> place(commands, tenant, code, command));
    }

    Reply placeOrder(CommandBus bus, String tenant, String code, String body) {
        return invoke(201, () -> place(bus, tenant, code, json.readValue(body, PlaceTradeOrderCmd.class)));
    }

    private static Object place(CommandBus bus, String tenant, String code, PlaceTradeOrderCmd command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("下单命令不能为空");
        }
        command.setTenantId(tenant);
        command.setOrderNo(code);
        return bus.execute(command).data().orElseThrow();
    }

    @Override
    public Reply confirmPayment(String tenant, String orderNo, String body) {
        return confirmPayment(commands, tenant, orderNo, body);
    }

    Reply confirmPayment(CommandBus bus, String tenant, String orderNo, String body) {
        return invoke(200, () -> {
            var field = json.readTree(body).path("paidAmountCent");
            if (!field.isIntegralNumber() || !field.canConvertToLong()) {
                throw new IllegalArgumentException("支付金额须为分值整数");
            }
            return bus.execute(new ConfirmTradePaymentCmd(tenant, orderNo, field.longValue())).data().orElseThrow();
        });
    }

    public Reply confirmPayment(String tenant, String orderNo, Long amountCent) {
        return confirmPayment(commands, tenant, orderNo, amountCent);
    }

    Reply confirmPayment(CommandBus bus, String tenant, String orderNo, Long amountCent) {
        return invoke(200, () -> bus.execute(new ConfirmTradePaymentCmd(tenant, orderNo, amountCent)).data().orElseThrow());
    }

    public Reply cancelOrder(String tenant, String orderNo) {
        return changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.CANCEL);
    }

    public Reply fulfillOrder(String tenant, String orderNo) {
        return changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.FULFILL);
    }

    Reply changeOrder(CommandBus bus, String tenant, String orderNo, ChangeTradeOrderCmd.Action action) {
        return invoke(200, () -> bus.execute(new ChangeTradeOrderCmd(tenant, orderNo, action)).data().orElseThrow());
    }

    public Reply goods(String tenant, String code) {
        return invoke(200, () -> queries.ask(new GoodsSpuQuery(tenant, code)));
    }

    public Reply order(String tenant, String code) {
        return invoke(200, () -> queries.ask(new TradeOrderQuery(tenant, code)));
    }

    CatalogOrderView orderView() {
        return new CatalogOrderView(query -> queries.ask(query));
    }

    Reply projectedOrder(QueryBus bus, String tenant, String code) {
        return invoke(200, () -> bus.ask(new ProjectedTradeOrderQuery(tenant, code)));
    }

    Reply project(CatalogOrderView view, String tenant, String code) {
        if (Objects.isNull(tenant) || Objects.isNull(code)) {
            return reply(400, Map.of("error", "请求字段或金额无效"));
        }
        return invoke(200, () -> view.rebuild(new TradeOrderQuery(tenant, code)));
    }

    private Reply invoke(int status, Operation operation) {
        Object result;
        try {
            result = operation.run();
        } catch (JsonProcessingException | IllegalArgumentException | ArithmeticException ex) {
            return reply(400, Map.of("error", "请求字段或金额无效"));
        } catch (TradeOrderStateConflict ex) {
            return reply(409, Map.of("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return reply(404, Map.of("error", ex.getMessage()));
        } catch (DuplicateTradeKey ex) {
            return reply(409, Map.of("error", ex.getMessage()));
        }
        return reply(status, result);
    }

    private Reply reply(int status, Object value) {
        return new Reply(status, json.valueToTree(value));
    }

    private interface Operation {
        Object run() throws JsonProcessingException;
    }
}
