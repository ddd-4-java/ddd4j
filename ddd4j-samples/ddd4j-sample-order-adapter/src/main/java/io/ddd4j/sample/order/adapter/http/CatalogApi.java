package io.ddd4j.sample.order.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Collections;
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
    /** \u53ef\u4f9b\u6846\u67b6\u547d\u4ee4\u603b\u7ebf\u6ce8\u518c\u7684\u5546\u54c1\u4ea4\u6613\u547d\u4ee4\u6267\u884c\u5668\u3002 */
    private final CatalogCommands executor;
    /** \u5904\u7406\u9ed8\u8ba4 HTTP \u5199\u8bf7\u6c42\u7684\u547d\u4ee4\u603b\u7ebf\u3002 */
    private final CommandBus commands;
    /** \u67e5\u8be2\u5546\u54c1\u548c\u8ba2\u5355\u4e8b\u5b9e\u7684\u67e5\u8be2\u603b\u7ebf\u3002 */
    private final QueryBus queries;
    /** \u89e3\u6790\u8bf7\u6c42\u53ca\u6784\u9020 JSON \u54cd\u5e94\u7684\u6620\u5c04\u5668\u3002 */
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    /**
     * 组装商品交易用例入口。
     *
     * @param service 商品交易应用服务
     */
    public CatalogApi(CatalogService service) {
        executor = new CatalogCommands(service);
        commands = new DefaultCommandBus(Collections.singletonList(executor));
        queries = new DefaultQueryBus(Arrays.asList(new GoodsSpuQueryHandler(service), new TradeOrderQueryHandler(service)));
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

    /**
     * \u89e3\u6790\u5546\u54c1\u8bf7\u6c42\u5e76\u4f7f\u7528\u6307\u5b9a\u547d\u4ee4\u603b\u7ebf\u521b\u5efa\u5546\u54c1\u3002
     * @param bus \u8fd0\u884c\u6846\u67b6\u88c5\u914d\u7684\u547d\u4ee4\u603b\u7ebf
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u5546\u54c1\u7f16\u7801
     * @param body \u5546\u54c1\u521b\u5efa\u8bf7\u6c42\u7684 JSON \u6b63\u6587
     * @return HTTP \u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     */
    Reply createGoods(CommandBus bus, String tenant, String code, String body) {
        return invoke(201, () -> create(bus, tenant, code, json.readValue(body, CreateGoodsSpuCmd.class)));
    }

    /**
     * \u7ed1\u5b9a\u8bf7\u6c42\u8def\u5f84\u4e2d\u7684\u79df\u6237\u4e0e\u5546\u54c1\u7f16\u7801\uff0c\u518d\u63d0\u4ea4\u5546\u54c1\u521b\u5efa\u547d\u4ee4\u3002
     * @param bus \u8fd0\u884c\u6846\u67b6\u88c5\u914d\u7684\u547d\u4ee4\u603b\u7ebf
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u5546\u54c1\u7f16\u7801
     * @param command \u4ece\u8bf7\u6c42\u6b63\u6587\u89e3\u6790\u7684\u5546\u54c1\u547d\u4ee4
     * @return \u547d\u4ee4\u6267\u884c\u7ed3\u679c
     * @throws IllegalArgumentException \u5546\u54c1\u547d\u4ee4\u4e3a\u7a7a
     */
    private static Object create(CommandBus bus, String tenant, String code, CreateGoodsSpuCmd command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("商品命令不能为空");
        }
        command.setTenantId(tenant);
        command.setSpuCode(code);
        return bus.execute(command).data().orElseThrow(NoSuchElementException::new);
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

    /**
     * \u89e3\u6790\u4e0b\u5355\u8bf7\u6c42\u5e76\u4f7f\u7528\u6307\u5b9a\u547d\u4ee4\u603b\u7ebf\u63d0\u4ea4\u8ba2\u5355\u3002
     * @param bus \u8fd0\u884c\u6846\u67b6\u88c5\u914d\u7684\u547d\u4ee4\u603b\u7ebf
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u8ba2\u5355\u53f7
     * @param body \u4e0b\u5355\u8bf7\u6c42\u7684 JSON \u6b63\u6587
     * @return HTTP \u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     */
    Reply placeOrder(CommandBus bus, String tenant, String code, String body) {
        return invoke(201, () -> place(bus, tenant, code, json.readValue(body, PlaceTradeOrderCmd.class)));
    }

    /**
     * \u4ee5\u8bf7\u6c42\u8def\u5f84\u4e2d\u7684\u79df\u6237\u4e0e\u8ba2\u5355\u53f7\u63d0\u4ea4\u4e0b\u5355\u547d\u4ee4\u3002
     * @param bus \u8fd0\u884c\u6846\u67b6\u88c5\u914d\u7684\u547d\u4ee4\u603b\u7ebf
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u8ba2\u5355\u53f7
     * @param command \u4ece\u8bf7\u6c42\u6b63\u6587\u89e3\u6790\u7684\u4e0b\u5355\u547d\u4ee4
     * @return \u547d\u4ee4\u6267\u884c\u7ed3\u679c
     * @throws IllegalArgumentException \u4e0b\u5355\u547d\u4ee4\u4e3a\u7a7a
     */
    private static Object place(CommandBus bus, String tenant, String code, PlaceTradeOrderCmd command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("下单命令不能为空");
        }
        command.setTenantId(tenant);
        command.setOrderNo(code);
        return bus.execute(command).data().orElseThrow(NoSuchElementException::new);
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

    /**
     * \u4ece JSON \u8bfb\u53d6\u80fd\u7528\u957f\u6574\u6570\u8868\u793a\u7684\u4eba\u6c11\u5e01\u5206\u503c\u5e76\u63d0\u4ea4\u652f\u4ed8\u786e\u8ba4\u3002
     * @param bus \u8fd0\u884c\u6846\u67b6\u88c5\u914d\u7684\u547d\u4ee4\u603b\u7ebf
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param orderNo \u79df\u6237\u5185\u8ba2\u5355\u53f7
     * @param body \u5305\u542b\u652f\u4ed8\u91d1\u989d\u7684 JSON \u6b63\u6587
     * @return HTTP \u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     */
    Reply confirmPayment(CommandBus bus, String tenant, String orderNo, String body) {
        return invoke(200, () -> {
            JsonNode field = json.readTree(body).path("paidAmountCent");
            if (!field.isIntegralNumber() || !field.canConvertToLong()) {
                throw new IllegalArgumentException("支付金额须为分值整数");
            }
            return bus.execute(new ConfirmTradePaymentCmd(tenant, orderNo, field.longValue())).data().orElseThrow(NoSuchElementException::new);
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

    /**
     * \u5c06\u5df2\u89e3\u6790\u7684\u5b9e\u4ed8\u91d1\u989d\u63d0\u4ea4\u7ed9\u8fd0\u884c\u6846\u67b6\u7684\u547d\u4ee4\u603b\u7ebf\u3002
     * @param bus \u8fd0\u884c\u6846\u67b6\u88c5\u914d\u7684\u547d\u4ee4\u603b\u7ebf
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param orderNo \u79df\u6237\u5185\u8ba2\u5355\u53f7
     * @param amountCent \u5b9e\u4ed8\u91d1\u989d\uff0c\u5355\u4f4d\u4e3a\u4eba\u6c11\u5e01\u5206
     * @return HTTP \u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     */
    Reply confirmPayment(CommandBus bus, String tenant, String orderNo, Long amountCent) {
        return invoke(200, () -> bus.execute(new ConfirmTradePaymentCmd(tenant, orderNo, amountCent)).data().orElseThrow(NoSuchElementException::new));
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

    /**
     * \u63d0\u4ea4\u8ba2\u5355\u53d6\u6d88\u6216\u5c65\u7ea6\u5b8c\u6210\u547d\u4ee4\u3002
     * @param bus \u8fd0\u884c\u6846\u67b6\u88c5\u914d\u7684\u547d\u4ee4\u603b\u7ebf
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param orderNo \u79df\u6237\u5185\u8ba2\u5355\u53f7
     * @param action \u8ba2\u5355\u72b6\u6001\u53d8\u66f4\u52a8\u4f5c
     * @return HTTP \u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     */
    Reply changeOrder(CommandBus bus, String tenant, String orderNo, ChangeTradeOrderCmd.Action action) {
        return invoke(200, () -> bus.execute(new ChangeTradeOrderCmd(tenant, orderNo, action)).data().orElseThrow(NoSuchElementException::new));
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

    /**
     * \u521b\u5efa\u4e00\u4e2a\u901a\u8fc7\u67e5\u8be2\u603b\u7ebf\u8bfb\u53d6\u5df2\u63d0\u4ea4\u8ba2\u5355\u7684\u6295\u5f71\u3002
     * @return \u65b0\u5efa\u7684\u8ba2\u5355\u6295\u5f71\u89c6\u56fe
     */
    CatalogOrderView orderView() {
        return new CatalogOrderView(query -> queries.ask(query));
    }

    /**
     * \u901a\u8fc7\u6307\u5b9a\u67e5\u8be2\u603b\u7ebf\u8bfb\u53d6\u8ba2\u5355\u5feb\u7167\u3002
     * @param bus \u6ce8\u518c\u6295\u5f71\u67e5\u8be2\u5904\u7406\u5668\u7684\u67e5\u8be2\u603b\u7ebf
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u8ba2\u5355\u53f7
     * @return HTTP \u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     */
    Reply projectedOrder(QueryBus bus, String tenant, String code) {
        return invoke(200, () -> bus.ask(new ProjectedTradeOrderQuery(tenant, code)));
    }

    /**
     * \u6821\u9a8c\u67e5\u8be2\u6807\u8bc6\u5e76\u4ece\u5df2\u63d0\u4ea4\u8ba2\u5355\u91cd\u5efa\u5f53\u524d\u6295\u5f71\u3002
     * @param view \u9700\u8981\u91cd\u5efa\u7684\u8ba2\u5355\u6295\u5f71\u89c6\u56fe
     * @param tenant \u8bf7\u6c42\u6240\u5c5e\u79df\u6237\u6807\u8bc6
     * @param code \u79df\u6237\u5185\u8ba2\u5355\u53f7
     * @return HTTP \u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     */
    Reply project(CatalogOrderView view, String tenant, String code) {
        if (Objects.isNull(tenant) || Objects.isNull(code)) {
            return reply(400, Collections.singletonMap("error", "请求字段或金额无效"));
        }
        return invoke(200, () -> view.rebuild(new TradeOrderQuery(tenant, code)));
    }

    /**
     * \u6267\u884c\u64cd\u4f5c\uff0c\u5e76\u5c06\u65e0\u6548\u8bf7\u6c42\u3001\u72b6\u6001\u51b2\u7a81\u548c\u8bb0\u5f55\u7f3a\u5931\u8f6c\u6362\u6210 HTTP \u54cd\u5e94\u3002
     * @param status \u64cd\u4f5c\u6210\u529f\u65f6\u4f7f\u7528\u7684 HTTP \u72b6\u6001\u7801
     * @param operation \u9700\u8981\u6267\u884c\u7684\u4e1a\u52a1\u64cd\u4f5c
     * @return HTTP \u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     */
    private Reply invoke(int status, Operation operation) {
        Object result;
        try {
            result = operation.run();
        } catch (JsonProcessingException | IllegalArgumentException | ArithmeticException ex) {
            return reply(400, Collections.singletonMap("error", "请求字段或金额无效"));
        } catch (TradeOrderStateConflict ex) {
            return reply(409, Collections.singletonMap("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return reply(404, Collections.singletonMap("error", ex.getMessage()));
        } catch (DuplicateTradeKey ex) {
            return reply(409, Collections.singletonMap("error", ex.getMessage()));
        }
        return reply(status, result);
    }

    /**
     * \u5c06\u4e1a\u52a1\u6216\u9519\u8bef\u7ed3\u679c\u8f6c\u6362\u6210 JSON \u54cd\u5e94\u3002
     * @param status HTTP \u72b6\u6001\u7801
     * @param value \u5f85\u5e8f\u5217\u5316\u7684\u54cd\u5e94\u6b63\u6587
     * @return HTTP \u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     */
    private Reply reply(int status, Object value) {
        return new Reply(status, json.valueToTree(value));
    }

    /** \u53ef\u80fd\u56e0 JSON \u5904\u7406\u5931\u8d25\u800c\u629b\u51fa\u5f02\u5e38\u7684 HTTP \u4e1a\u52a1\u64cd\u4f5c\u3002 */
    private interface Operation {
        /**
         * \u6267\u884c\u4e00\u6b21\u5546\u54c1\u6216\u8ba2\u5355\u4e1a\u52a1\u64cd\u4f5c\u3002
         * @return \u5f85\u5199\u5165\u54cd\u5e94\u6b63\u6587\u7684\u64cd\u4f5c\u7ed3\u679c
         * @throws JsonProcessingException \u65e0\u6cd5\u5904\u7406\u8bf7\u6c42 JSON
         */
        Object run() throws JsonProcessingException;
    }
}
