package io.ddd4j.sample.dropwizard.shiro;

import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.core.util.SubjectKit;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.Path;

@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogResource {
    public CatalogTradeResource(CatalogEndpoints catalog) {
        super(catalog);
    }

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
