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
    private final CatalogApi api;
    private final CommandBus commands;
    private final CatalogOrderView view;
    private final QueryBus projectedQueries;

    public CatalogCqrs(CatalogApi api, CommandBus commands) {
        this.api = api;
        this.commands = commands;
        view = api.orderView();
        projectedQueries = new DefaultQueryBus(List.of(view));
    }

    @Override
    public Reply createGoods(String tenant, String code, String body) {
        return api.createGoods(commands, tenant, code, body);
    }

    @Override
    public Reply goods(String tenant, String code) {
        return api.goods(tenant, code);
    }

    @Override
    public Reply placeOrder(String tenant, String code, String body) {
        return api.placeOrder(commands, tenant, code, body);
    }

    @Override
    public Reply confirmPayment(String tenant, String orderNo, String body) {
        return api.confirmPayment(commands, tenant, orderNo, body);
    }

    @Override
    public Reply cancelOrder(String tenant, String orderNo) {
        return api.changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.CANCEL);
    }

    @Override
    public Reply fulfillOrder(String tenant, String orderNo) {
        return api.changeOrder(commands, tenant, orderNo, ChangeTradeOrderCmd.Action.FULFILL);
    }

    @Override
    public Reply order(String tenant, String code) {
        if (Objects.isNull(tenant) || Objects.isNull(code)) {
            return api.order(tenant, code);
        }
        return api.projectedOrder(projectedQueries, tenant, code);
    }

    public Reply project(String tenant, String code) {
        return api.project(view, tenant, code);
    }

    public static CommandExecutor<Command> executor(CatalogApi api) {
        return api.executor();
    }

}
