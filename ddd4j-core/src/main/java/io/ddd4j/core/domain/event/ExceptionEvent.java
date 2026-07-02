package io.ddd4j.core.domain.event;

import lombok.Getter;

import java.io.Serial;
import java.util.Objects;

/**
 * Exception domain event used by runtime adapters to publish request failures.
 *
 * <p>The event remains in core as a framework-neutral object. Spring, Quarkus,
 * Guice or other runtimes can publish it through their own event mechanism
 * without leaking framework APIs into core.</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
public class ExceptionEvent extends DomainEvent<Exception> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Object source;
    private final Exception payload;

    public ExceptionEvent(Object source, Exception payload) {
        super(Objects.requireNonNull(payload, "payload must not be null"));
        this.source = source;
        this.payload = payload;
    }
}
