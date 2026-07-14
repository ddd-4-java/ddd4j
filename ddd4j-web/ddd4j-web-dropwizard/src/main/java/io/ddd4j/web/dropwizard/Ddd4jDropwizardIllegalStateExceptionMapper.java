package io.ddd4j.web.dropwizard;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import java.util.Objects;

/**
 * 覆盖 Dropwizard 默认的 500 映射，将非法领域状态交给 ddd4j 统一翻译为 409。
 */
public final class Ddd4jDropwizardIllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    private final Ddd4jDropwizardExceptionMapper delegate;

    public Ddd4jDropwizardIllegalStateExceptionMapper() {
        this(new Ddd4jDropwizardExceptionMapper());
    }

    public Ddd4jDropwizardIllegalStateExceptionMapper(Ddd4jDropwizardExceptionMapper delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public Response toResponse(IllegalStateException exception) {
        return delegate.toResponse(exception);
    }
}
