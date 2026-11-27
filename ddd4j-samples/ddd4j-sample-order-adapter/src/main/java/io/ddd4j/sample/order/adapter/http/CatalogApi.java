package io.ddd4j.sample.order.adapter.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.query.DefaultQueryBus;
import io.ddd4j.core.cqrs.query.QueryBus;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.app.executor.CatalogCommands;
import io.ddd4j.sample.order.app.executor.GoodsSpuQueryHandler;
import io.ddd4j.sample.order.app.executor.TradeOrderQueryHandler;
import io.ddd4j.sample.order.app.projection.CatalogOrderView;
import io.ddd4j.sample.order.app.projection.ProjectedTradeOrderQuery;
import io.ddd4j.sample.order.client.command.ChangeTradeOrderCmd;
import io.ddd4j.sample.order.client.command.ConfirmTradePaymentCmd;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;
import io.ddd4j.sample.order.client.query.GoodsSpuQuery;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.exception.DuplicateTradeKey;
import io.ddd4j.sample.order.domain.exception.TradeOrderStateConflict;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * 商品交易应用服务的 HTTP 适配入口。
 */
public class CatalogApi implements CatalogEndpoints {
    /**
     * 可供框架命令总线注册的商品交易命令执行器。
     */
    private final CatalogCommands executor;
    /**
     * 处理默认 HTTP 写请求的命令总线。
     */
    private final CommandBus commands;
    /**
     * 查询商品和订单事实的查询总线。
     */
    private final QueryBus queries;
    /**
     * 解析请求及构造 JSON 响应的映射器。
     */
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
     * 绑定请求路径中的租户与商品编码，再提交商品创建命令。
     *
     * @param bus     运行框架装配的命令总线
     * @param tenant  请求所属租户标识
     * @param code    租户内商品编码
     * @param command 从请求正文解析的商品命令
     * @return 命令执行结果
     * @throws IllegalArgumentException 商品命令为空
     */
    private static Object create(CommandBus bus, String tenant, String code, CreateGoodsSpuCmd command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("商品命令不能为空");
        }
        command.setTenantId(tenant);
        command.setSpuCode(code);
        return Optional.ofNullable(bus.execute(command).getData()).orElseThrow();
    }

    /**
     * 以请求路径中的租户与订单号提交下单命令。
     *
     * @param bus     运行框架装配的命令总线
     * @param tenant  请求所属租户标识
     * @param code    租户内订单号
     * @param command 从请求正文解析的下单命令
     * @return 命令执行结果
     * @throws IllegalArgumentException 下单命令为空
     */
    private static Object place(CommandBus bus, String tenant, String code, PlaceTradeOrderCmd command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("下单命令不能为空");
        }
        command.setTenantId(tenant);
        command.setOrderNo(code);
        return Optional.ofNullable(bus.execute(command).getData()).orElseThrow();
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
     * @param code   租户内业务编号
     * @param body   请求 JSON 正文
     * @return 业务操作响应
     */
    public Reply createGoods(String tenant, String code, String body) {
        return createGoods(commands, tenant, code, body);
    }

    /**
     * 创建商品和初始规格。
     *
     * @param tenant  租户编号
     * @param code    租户内业务编号
     * @param command 已解析的业务命令
     * @return 业务操作响应
     */
    public Reply createGoods(String tenant, String code, CreateGoodsSpuCmd command) {
        return invoke(201, () -> create(commands, tenant, code, command));
    }

    /**
     * 解析商品请求并使用指定命令总线创建商品。
     *
     * @param bus    运行框架装配的命令总线
     * @param tenant 请求所属租户标识
     * @param code   租户内商品编码
     * @param body   商品创建请求的 JSON 正文
     * @return HTTP 状态码及响应正文
     */
    Reply createGoods(CommandBus bus, String tenant, String code, String body) {
        return invoke(201, () -> create(bus, tenant, code, json.readValue(body, CreateGoodsSpuCmd.class)));
    }

    /**
     * 创建交易订单。
     *
     * @param tenant 租户编号
     * @param code   租户内业务编号
     * @param body   请求 JSON 正文
     * @return 业务操作响应
     */
    public Reply placeOrder(String tenant, String code, String body) {
        return placeOrder(commands, tenant, code, body);
    }

    /**
     * 创建交易订单。
     *
     * @param tenant  租户编号
     * @param code    租户内业务编号
     * @param command 已解析的业务命令
     * @return 业务操作响应
     */
    public Reply placeOrder(String tenant, String code, PlaceTradeOrderCmd command) {
        return invoke(201, () -> place(commands, tenant, code, command));
    }

    /**
     * 解析下单请求并使用指定命令总线提交订单。
     *
     * @param bus    运行框架装配的命令总线
     * @param tenant 请求所属租户标识
     * @param code   租户内订单号
     * @param body   下单请求的 JSON 正文
     * @return HTTP 状态码及响应正文
     */
    Reply placeOrder(CommandBus bus, String tenant, String code, String body) {
        return invoke(201, () -> place(bus, tenant, code, json.readValue(body, PlaceTradeOrderCmd.class)));
    }

    /**
     * 确认订单支付。
     *
     * @param tenant  租户编号
     * @param orderNo 租户内订单编号
     * @param body    请求 JSON 正文
     * @return 业务操作响应
     */
    @Override
    public Reply confirmPayment(String tenant, String orderNo, String body) {
        return confirmPayment(commands, tenant, orderNo, body);
    }

    /**
     * 从 JSON 读取能用长整数表示的人民币分值并提交支付确认。
     *
     * @param bus     运行框架装配的命令总线
     * @param tenant  请求所属租户标识
     * @param orderNo 租户内订单号
     * @param body    包含支付金额的 JSON 正文
     * @return HTTP 状态码及响应正文
     */
    Reply confirmPayment(CommandBus bus, String tenant, String orderNo, String body) {
        return invoke(200, () -> {
            var field = json.readTree(body).path("paidAmountCent");
            if (!field.isIntegralNumber() || !field.canConvertToLong()) {
                throw new IllegalArgumentException("支付金额须为分值整数");
            }
            return Optional.ofNullable(bus.execute(new ConfirmTradePaymentCmd(tenant, orderNo, field.longValue())).getData()).orElseThrow();
        });
    }

    /**
     * 确认订单支付。
     *
     * @param tenant     租户编号
     * @param orderNo    租户内订单编号
     * @param amountCent 支付金额，单位为分
     * @return 业务操作响应
     */
    public Reply confirmPayment(String tenant, String orderNo, Long amountCent) {
        return confirmPayment(commands, tenant, orderNo, amountCent);
    }

    /**
     * 将已解析的实付金额提交给运行框架的命令总线。
     *
     * @param bus        运行框架装配的命令总线
     * @param tenant     请求所属租户标识
     * @param orderNo    租户内订单号
     * @param amountCent 实付金额，单位为人民币分
     * @return HTTP 状态码及响应正文
     */
    Reply confirmPayment(CommandBus bus, String tenant, String orderNo, Long amountCent) {
        return invoke(200, () -> Optional.ofNullable(bus.execute(new ConfirmTradePaymentCmd(tenant, orderNo, amountCent)).getData()).orElseThrow());
    }

    /**
     * 取消指定订单。
     *
     * @param tenant  租户编号
     * @param orderNo 租户内订单编号
     * @return 业务操作响应
     */
    public Reply cancelOrder(String tenant, String orderNo) {
        return changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.CANCEL);
    }

    /**
     * 完成指定订单的履约。
     *
     * @param tenant  租户编号
     * @param orderNo 租户内订单编号
     * @return 业务操作响应
     */
    public Reply fulfillOrder(String tenant, String orderNo) {
        return changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.FULFILL);
    }

    /**
     * 提交订单取消或履约完成命令。
     *
     * @param bus     运行框架装配的命令总线
     * @param tenant  请求所属租户标识
     * @param orderNo 租户内订单号
     * @param action  订单状态变更动作
     * @return HTTP 状态码及响应正文
     */
    Reply changeOrder(CommandBus bus, String tenant, String orderNo, ChangeTradeOrderCmd.Action action) {
        return invoke(200, () -> Optional.ofNullable(bus.execute(new ChangeTradeOrderCmd(tenant, orderNo, action)).getData()).orElseThrow());
    }

    /**
     * 查询指定商品。
     *
     * @param tenant 租户编号
     * @param code   租户内业务编号
     * @return 业务操作响应
     */
    public Reply goods(String tenant, String code) {
        return invoke(200, () -> queries.ask(new GoodsSpuQuery(tenant, code)));
    }

    /**
     * 查询指定订单。
     *
     * @param tenant 租户编号
     * @param code   租户内业务编号
     * @return 业务操作响应
     */
    public Reply order(String tenant, String code) {
        return invoke(200, () -> queries.ask(new TradeOrderQuery(tenant, code)));
    }

    /**
     * 创建一个通过查询总线读取已提交订单的投影。
     *
     * @return 新建的订单投影视图
     */
    CatalogOrderView orderView() {
        return new CatalogOrderView(query -> queries.ask(query));
    }

    /**
     * 通过指定查询总线读取订单快照。
     *
     * @param bus    注册投影查询处理器的查询总线
     * @param tenant 请求所属租户标识
     * @param code   租户内订单号
     * @return HTTP 状态码及响应正文
     */
    Reply projectedOrder(QueryBus bus, String tenant, String code) {
        return invoke(200, () -> bus.ask(new ProjectedTradeOrderQuery(tenant, code)));
    }

    /**
     * 校验查询标识并从已提交订单重建当前投影。
     *
     * @param view   需要重建的订单投影视图
     * @param tenant 请求所属租户标识
     * @param code   租户内订单号
     * @return HTTP 状态码及响应正文
     */
    Reply project(CatalogOrderView view, String tenant, String code) {
        if (Objects.isNull(tenant) || Objects.isNull(code)) {
            return reply(400, Map.of("error", "请求字段或金额无效"));
        }
        return invoke(200, () -> view.rebuild(new TradeOrderQuery(tenant, code)));
    }

    /**
     * 执行操作，并将无效请求、状态冲突和记录缺失转换成 HTTP 响应。
     *
     * @param status    操作成功时使用的 HTTP 状态码
     * @param operation 需要执行的业务操作
     * @return HTTP 状态码及响应正文
     */
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

    /**
     * 将业务或错误结果转换成 JSON 响应。
     *
     * @param status HTTP 状态码
     * @param value  待序列化的响应正文
     * @return HTTP 状态码及响应正文
     */
    private Reply reply(int status, Object value) {
        return new Reply(status, json.valueToTree(value));
    }

    /**
     * 可能因 JSON 处理失败而抛出异常的 HTTP 业务操作。
     */
    private interface Operation {
        /**
         * 执行一次商品或订单业务操作。
         *
         * @return 待写入响应正文的操作结果
         * @throws JsonProcessingException 无法处理请求 JSON
         */
        Object run() throws JsonProcessingException;
    }
}
