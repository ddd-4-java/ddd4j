package io.ddd4j.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionService;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.guice.cqrs.GuiceViewManager;

import java.util.Collection;
import java.util.List;

/**
 * 显式投影能力，与 Ddd4jGuiceModule.basic() 配合。
 * 必须绑定位置仓储和真实 EventChunkReader；缺少配置在 Injector 创建时失败。
 * 持久化实现由调用方选择已有 JDBC/JPA 仓储，本模块不创建存储或回退到内存/Noop。
 */
public final class Ddd4jProjectionGuiceModule extends AbstractModule {
    private final List<? extends ProjectionView<Object>> views;

    /**
     * 选择需要运行的视图。
     * @param views 业务投影视图
     */
    public Ddd4jProjectionGuiceModule(Collection<? extends ProjectionView<Object>> views) {
        this.views = List.copyOf(views);
    }

    @Override
    protected void configure() {
        requireBinding(ProjectionPositionRepository.class);
        requireBinding(Key.get(new TypeLiteral<EventChunkReader<Object>>() { }));
        bind(ViewManager.class).to(GuiceViewManager.class);
        bind(ViewScheduler.class).to(GuiceViewManager.class);
        bind(Startup.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    ProjectionService projectionService(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    @Provides
    @Singleton
    ProjectionRunner<Object> projectionRunner(ProjectionService service, EventChunkReader<Object> reader) {
        return new ProjectionRunner<>(service, reader);
    }

    @Provides
    @Singleton
    GuiceViewManager viewManager(ProjectionPositionRepository repository, ProjectionRunner<Object> runner) {
        return new GuiceViewManager(GuiceConstants.DEFAULT_THREAD_POOL_SIZE, repository, runner, views);
    }

    /** Injector 完成绑定后启动；关闭由 Ddd4jGuiceRuntime 统一释放。 */
    static final class Startup {
        @Inject
        Startup(GuiceViewManager manager) {
            manager.start();
        }
    }
}
