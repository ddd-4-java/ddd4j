package io.ddd4j.sample.helidon.cqrs;

import io.ddd4j.sample.helidon.cqrs.web.OrderResource;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;

/**
 * Helidon JAX-RS 应用声明。
 */
@ApplicationPath("/")
public class HelidonCqrsJaxRsApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(OrderResource.class);
    }
}
