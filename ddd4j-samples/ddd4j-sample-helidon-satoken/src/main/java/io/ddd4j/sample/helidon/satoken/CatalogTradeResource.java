package io.ddd4j.sample.helidon.satoken;

import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.adapter.http.CatalogResource;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;

/**
 * Helidon 商品交易 HTTP 入口，转发商品和订单请求到共用的交易用例。
 */
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogResource {
    /**
     * 将传入的商品交易操作绑定到 HTTP 入口。
     *
     * @param catalog 已装配的商品交易 HTTP 操作
     */
    public CatalogTradeResource(CatalogEndpoints catalog) {
        super(catalog);
    }

    /**
     * 要求当前主体已登录并拥有请求操作所需的订单权限。
     *
     * @param method 操作类别，用于选择所需的订单权限
     * @throws NotAuthorizedException 当前主体未登录
     * @throws ForbiddenException     当前主体缺少所需订单权限
     */
    @Override
    protected void authorize(String method) {
        var subject = SubjectKit.getSubject();
        if (!subject.isAuthenticated()) {
            throw new NotAuthorizedException("Bearer");
        }
        String permission = switch (method) {
            case "GET" -> "order:read";
            case "CANCEL" -> "order:cancel";
            default -> "order:write";
        };
        if (!subject.isPermitted(permission)) {
            throw new ForbiddenException();
        }
    }
}
