package io.ddd4j.sample.quarkus.cqrs;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.start.CatalogRuntime;
import java.util.Set;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@jakarta.inject.Singleton
public class CatalogCommandExecutor implements CommandExecutor<Command> {
    private final CatalogApi api;
    private final CommandExecutor<Command> delegate;

    @Inject
    public CatalogCommandExecutor(@ConfigProperty(name = "catalog.jdbc.url") String url,
                                  @ConfigProperty(name = "catalog.db.user") String username,
                                  @ConfigProperty(name = "catalog.db.password") String password,
                                  io.ddd4j.core.ddd.event.DomainEventPublisher publisher) {
        api = CatalogRuntime.start(url, username, password, publisher);
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
