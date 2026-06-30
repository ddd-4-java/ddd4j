package io.ddd4j.guice.data.logs;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.ddd4j.data.logs.aspect.ApiOperationLogProvider;
import io.ddd4j.data.logs.aspect.DefaultApiOperationLogProvider;
import jakarta.inject.Singleton;

/**
 * ddd4j API 操作日志的 Guice 桥接模块。
 */
public class Ddd4jLogsGuiceModule extends AbstractModule {

    @Provides
    @Singleton
    public ApiOperationLogProvider apiOperationLogProvider() {
        return new DefaultApiOperationLogProvider();
    }
}
