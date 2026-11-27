package io.ddd4j.web.core.idempotency;

import io.ddd4j.web.core.context.WebRequestContext;

/**
 * 标识由应用事务自行处理重试的入口。应用通过 ServiceLoader 注册，其他请求继续使用 Web 幂等租约。
 */
@FunctionalInterface
public interface ApplicationIdempotencyPolicy {
    /**
     * 判断当前请求能否直接交给能安全重放的业务处理器。
     *
     * @param context 当前 Web 请求上下文
     * @return 此路由由业务处理器保证幂等时为 true
     */
    boolean handles(WebRequestContext context);
}
