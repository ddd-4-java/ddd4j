package io.ddd4j.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.ddd4j.data.logs.aspect.ApiOperationLogProvider;
import io.ddd4j.data.logs.aspect.DefaultApiOperationLogProvider;
import jakarta.inject.Singleton;

/**
 * ddd4j API 操作日志的 Guice 桥接模块。
 * <p>
 * 提供 {@link ApiOperationLogProvider} 的 Guice 绑定，
 * 业务方 install 此模块后即可注入使用 API 操作日志能力。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class Ddd4jLogsGuiceModule extends AbstractModule {

    /**
     * 提供 API 操作日志提供者。
     *
     * @return ApiOperationLogProvider 实例
     */
    @Provides
    @Singleton
    public ApiOperationLogProvider apiOperationLogProvider() {
        return new DefaultApiOperationLogProvider();
    }
}
