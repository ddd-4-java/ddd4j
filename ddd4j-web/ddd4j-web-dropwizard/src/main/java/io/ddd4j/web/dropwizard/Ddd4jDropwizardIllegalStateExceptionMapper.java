package io.ddd4j.web.dropwizard;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import java.util.Objects;

/**
 * 覆盖 Dropwizard 默认的 500 映射，将非法领域状态交给 ddd4j 统一翻译为 409。
 */
public final class Ddd4jDropwizardIllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    /** \u6267\u884c\u5b9e\u9645\u5f02\u5e38\u8f6c\u6362\u7684\u6846\u67b6\u6620\u5c04\u5668\u3002 */
    private final Ddd4jDropwizardExceptionMapper delegate;

    /** \u4f7f\u7528\u9ed8\u8ba4 ddd4j \u5f02\u5e38\u6620\u5c04\u5668\u3002 */
    public Ddd4jDropwizardIllegalStateExceptionMapper() {
        this(new Ddd4jDropwizardExceptionMapper());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u59d4\u6258\u5904\u7406\u975e\u6cd5\u72b6\u6001\u5f02\u5e38\u3002
     * @param delegate ddd4j \u5f02\u5e38\u6620\u5c04\u5668
     */
    public Ddd4jDropwizardIllegalStateExceptionMapper(Ddd4jDropwizardExceptionMapper delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * \u628a\u5f02\u5e38\u4ea4\u7ed9\u5171\u7528\u6846\u67b6\u6620\u5c04\u5668\u751f\u6210 HTTP \u9519\u8bef\u54cd\u5e94\u3002
     * @param exception \u5f85\u6620\u5c04\u7684\u8bf7\u6c42\u5f02\u5e38
     * @return \u7531\u5171\u7528\u6620\u5c04\u5668\u8fd4\u56de\u7684 HTTP \u54cd\u5e94
     */
    @Override
    public Response toResponse(IllegalStateException exception) {
        return delegate.toResponse(exception);
    }
}
