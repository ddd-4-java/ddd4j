package io.ddd4j.sample.dropwizard.satoken;

import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.core.util.SubjectKit;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;

@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogResource {
    public CatalogTradeResource(CatalogEndpoints catalog) {
        super(catalog);
    }

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
