package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.spring.event.SpringDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Set;
import org.springframework.core.env.Environment;

@org.springframework.stereotype.Component
public class CatalogCommandExecutor implements CommandExecutor<Command> {
    private final CatalogApi api;
    private final CommandExecutor<Command> delegate;

    public CatalogCommandExecutor(Environment environment, ApplicationEventPublisher publisher) {
        api = CatalogRuntime.start(environment.getRequiredProperty("CATALOG_JDBC_URL"),
                environment.getRequiredProperty("CATALOG_DB_USER"), environment.getRequiredProperty("CATALOG_DB_PASSWORD"),
                new SpringDomainEventPublisher(publisher));
        delegate = CatalogCqrs.executor(api);
    }

    public CatalogApi api() {
        return api;
    }

    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return delegate.supportedCommands();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result<?> execute(Command command) {
        return delegate.execute(command);
    }
}
