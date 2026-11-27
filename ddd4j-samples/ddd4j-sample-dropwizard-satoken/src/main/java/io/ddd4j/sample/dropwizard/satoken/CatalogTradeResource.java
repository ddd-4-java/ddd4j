package io.ddd4j.sample.dropwizard.satoken;

import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.core.util.SubjectKit;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.Path;

/** Dropwizard \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogResource {
    /**
     * \u5c06\u4f20\u5165\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u7ed1\u5b9a\u5230 HTTP \u5165\u53e3\u3002
     * @param catalog \u5df2\u88c5\u914d\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c
     */
    public CatalogTradeResource(CatalogEndpoints catalog) {
        super(catalog);
    }

    /**
     * \u8981\u6c42\u5f53\u524d\u4e3b\u4f53\u5df2\u767b\u5f55\u5e76\u62e5\u6709\u8bf7\u6c42\u64cd\u4f5c\u6240\u9700\u7684\u8ba2\u5355\u6743\u9650\u3002
     * @param method \u64cd\u4f5c\u7c7b\u522b\uff0c\u7528\u4e8e\u9009\u62e9\u6240\u9700\u7684\u8ba2\u5355\u6743\u9650
     * @throws NotAuthorizedException \u5f53\u524d\u4e3b\u4f53\u672a\u767b\u5f55
     * @throws ForbiddenException \u5f53\u524d\u4e3b\u4f53\u7f3a\u5c11\u6240\u9700\u8ba2\u5355\u6743\u9650
     */
    @Override
    protected void authorize(String method) {
        io.ddd4j.core.subject.Subject subject = SubjectKit.getSubject();
        if (!subject.isAuthenticated()) {
            throw new NotAuthorizedException("Bearer");
        }
        String permission;
        switch (method) {
            case "GET":
                permission = "order:read";
                break;
            case "CANCEL":
                permission = "order:cancel";
                break;
            default:
                permission = "order:write";
                break;
        }
        if (!subject.isPermitted(permission)) {
            throw new ForbiddenException();
        }
    }
}
