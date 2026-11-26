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

/** 商品交易应用服务的 HTTP 适配入口。 */
public class CatalogApi implements CatalogEndpoints {
    private final CatalogCommands executor;
    private final CommandBus commands;
    private final QueryBus queries;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    /**
     * 组装商品交易用例入口。
     *
     * @param service 商品交易应用服务
     */
    public CatalogApi(CatalogService service) {
        executor = new CatalogCommands(service);
        commands = new DefaultCommandBus(List.of(executor));
        queries = new DefaultQueryBus(List.of(new GoodsSpuQueryHandler(service), new TradeOrderQueryHandler(service)));
    }

    /**
     * 获取商品交易命令执行器。
     *
     * @return 命令执行器
     */
    public CatalogCommands executor() {
        return executor;
    }

    /**
     * 创建商品和初始规格。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @param body 请求 JSON 正文
     * @return 业务操作响应
     */
    public Reply createGoods(String tenant, String code, String body) {
        return createGoods(commands, tenant, code, body);
    }

    /**
     * 创建商品和初始规格。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @param command 已解析的业务命令
     * @return 业务操作响应
     */
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

    /**
     * 创建交易订单。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @param body 请求 JSON 正文
     * @return 业务操作响应
     */
    public Reply placeOrder(String tenant, String code, String body) {
        return placeOrder(commands, tenant, code, body);
    }

    /**
     * 创建交易订单。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @param command 已解析的业务命令
     * @return 业务操作响应
     */
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

    /**
     * 确认订单支付。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @param body 请求 JSON 正文
     * @return 业务操作响应
     */
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

    /**
     * 确认订单支付。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @param amountCent 支付金额，单位为分
     * @return 业务操作响应
     */
    public Reply confirmPayment(String tenant, String orderNo, Long amountCent) {
        return confirmPayment(commands, tenant, orderNo, amountCent);
    }

    Reply confirmPayment(CommandBus bus, String tenant, String orderNo, Long amountCent) {
        return invoke(200, () -> bus.execute(new ConfirmTradePaymentCmd(tenant, orderNo, amountCent)).data().orElseThrow());
    }

    /**
     * 取消指定订单。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @return 业务操作响应
     */
    public Reply cancelOrder(String tenant, String orderNo) {
        return changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.CANCEL);
    }

    /**
     * 完成指定订单的履约。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @return 业务操作响应
     */
    public Reply fulfillOrder(String tenant, String orderNo) {
        return changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.FULFILL);
    }

    Reply changeOrder(CommandBus bus, String tenant, String orderNo, ChangeTradeOrderCmd.Action action) {
        return invoke(200, () -> bus.execute(new ChangeTradeOrderCmd(tenant, orderNo, action)).data().orElseThrow());
    }

    /**
     * 查询指定商品。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @return 业务操作响应
     */
    public Reply goods(String tenant, String code) {
        return invoke(200, () -> queries.ask(new GoodsSpuQuery(tenant, code)));
    }

    /**
     * 查询指定订单。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @return 业务操作响应
     */
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
