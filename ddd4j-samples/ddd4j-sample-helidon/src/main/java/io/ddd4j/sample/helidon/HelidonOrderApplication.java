package io.ddd4j.sample.helidon;

import io.ddd4j.web.helidon.Ddd4jHelidonExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonIllegalStateExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonRequestFilter;
import io.ddd4j.web.helidon.Ddd4jHelidonResponseFilter;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.Set;

/**
 * 共享 Order 业务内核的 Helidon MP JAX-RS 应用声明。
 */
@ApplicationPath("/")
public class HelidonOrderApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(HelidonOrderResource.class,
                Ddd4jHelidonRequestFilter.class,
                Ddd4jHelidonResponseFilter.class,
                Ddd4jHelidonExceptionMapper.class,
                Ddd4jHelidonIllegalStateExceptionMapper.class);
    }
}
