package io.ddd4j.sample.order.adapter.http;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import java.util.List;
import java.util.Objects;
import io.ddd4j.core.cqrs.query.DefaultQueryBus;
import io.ddd4j.core.cqrs.query.QueryBus;
import io.ddd4j.sample.order.app.projection.CatalogOrderView;
import io.ddd4j.sample.order.client.command.ChangeTradeOrderCmd;

/** 写入走框架命令总线。订单查询读取可从四表重建的独立读模型。 */
public final class CatalogCqrs implements CatalogEndpoints {
    /** \u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c\u3002 */
    private final CatalogApi api;
    /** CQRS \u793a\u4f8b\u7531\u8fd0\u884c\u6846\u67b6\u88c5\u914d\u7684\u547d\u4ee4\u603b\u7ebf\u3002 */
    private final CommandBus commands;
    /** \u4ece\u5df2\u63d0\u4ea4\u8ba2\u5355\u8bfb\u53d6\u6570\u636e\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    private final CatalogOrderView view;
    /** \u5904\u7406\u8ba2\u5355\u5feb\u7167\u67e5\u8be2\u7684\u603b\u7ebf\u3002 */
    private final QueryBus projectedQueries;

    /**
     * 绑定框架命令总线和商品交易用例。
     *
     * @param api 商品交易入口
     * @param commands 所用框架的命令总线
     */
    public CatalogCqrs(CatalogApi api, CommandBus commands) {
        this.api = api;
        this.commands = commands;
        view = api.orderView();
        projectedQueries = new DefaultQueryBus(List.of(view));
    }

    /** {@inheritDoc} */
    @Override
    public Reply createGoods(String tenant, String code, String body) {
        return api.createGoods(commands, tenant, code, body);
    }

    /** {@inheritDoc} */
    @Override
    public Reply goods(String tenant, String code) {
        return api.goods(tenant, code);
    }

    /** {@inheritDoc} */
    @Override
    public Reply placeOrder(String tenant, String code, String body) {
        return api.placeOrder(commands, tenant, code, body);
    }

    /** {@inheritDoc} */
    @Override
    public Reply confirmPayment(String tenant, String orderNo, String body) {
        return api.confirmPayment(commands, tenant, orderNo, body);
    }

    /** {@inheritDoc} */
    @Override
    public Reply cancelOrder(String tenant, String orderNo) {
        return api.changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.CANCEL);
    }

    /** {@inheritDoc} */
    @Override
    public Reply fulfillOrder(String tenant, String orderNo) {
        return api.changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.FULFILL);
    }

    /** {@inheritDoc} */
    @Override
    public Reply order(String tenant, String code) {
        if (Objects.isNull(tenant) || Objects.isNull(code)) {
            return api.order(tenant, code);
        }
        return api.projectedOrder(projectedQueries, tenant, code);
    }

    /**
     * 按已提交的订单快照重建读模型。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @return 业务操作响应
     */
    public Reply project(String tenant, String code) {
        return api.project(view, tenant, code);
    }

    /**
     * 获取商品交易命令执行器。
     *
     * @param api 商品交易入口
     * @return 命令执行器
     */
    public static CommandExecutor<Command> executor(CatalogApi api) {
        return api.executor();
    }

}
