package io.ddd4j.guice;

import com.google.inject.*;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.guice.cqrs.GuiceViewManager;

import java.util.Collection;
import java.util.List;

/**
 * 显式投影能力，与 Ddd4jGuiceModule.basic() 配合。
 * 必须绑定位置仓储和真实 EventChunkReader；缺少配置在 Injector 创建时失败。
 * 持久化实现由调用方选择已有 JDBC/JPA 仓储，本模块不创建存储或回退到内存/Noop。
 */
public final class Ddd4jProjectionGuiceModule extends AbstractModule {
    /**
     * 由当前运行环境管理和调度的投影视图。
     */
    private final List<? extends ProjectionView<Object>> views;

    /**
     * 选择需要运行的视图。
     *
     * @param views 业务投影视图
     */
    public Ddd4jProjectionGuiceModule(Collection<? extends ProjectionView<Object>> views) {
        this.views = java.util.Collections.unmodifiableList(views.stream().map(java.util.Objects::requireNonNull).collect(java.util.stream.Collectors.toList()));
    }

    /**
     * 为 Guice 装配投影服务、视图管理器及框架启动任务。
     */
    @Override
    protected void configure() {
        requireBinding(ProjectionPositionRepository.class);
        requireBinding(Key.get(new TypeLiteral<EventChunkReader<Object>>() {
        }));
        bind(ViewManager.class).to(GuiceViewManager.class);
        bind(ViewScheduler.class).to(GuiceViewManager.class);
        bind(Startup.class).asEagerSingleton();
    }

    /**
     * 用事件位点仓储装配共享投影处理服务。
     *
     * @param repository 保存并读取投影检查点的仓储
     * @return 投影处理服务实例
     */
    @Provides
    @Singleton
    ProjectionService projectionService(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    /**
     * 将事件读取端口与共享投影执行服务装配为运行器。
     *
     * @param service 负责投影事务及同步位点的服务组件
     * @param reader  从事件源按位点分批读取事件的组件
     * @return 执行已注册投影视图的投影运行器
     */
    @Provides
    @Singleton
    ProjectionRunner<Object> projectionRunner(ProjectionService service, EventChunkReader<Object> reader) {
        return new ProjectionRunner<>(service, reader);
    }

    /**
     * 以 Guice 生命周期组件装配投影视图管理器。
     *
     * @param repository 保存并读取投影检查点的仓储
     * @param runner     调用视图并维护事件位点的投影运行器
     * @return 当前应用的 Guice 视图管理器
     */
    @Provides
    @Singleton
    GuiceViewManager viewManager(ProjectionPositionRepository repository, ProjectionRunner<Object> runner) {
        return new GuiceViewManager(GuiceConstants.DEFAULT_THREAD_POOL_SIZE, repository, runner, views);
    }

    /**
     * Injector 完成绑定后启动；关闭由 Ddd4jGuiceRuntime 统一释放。
     */
    static final class Startup {
        /**
         * 取得 Guice 投影视图管理器并关联启动任务。
         *
         * @param manager 用于调度当前视图的管理器
         */
        @Inject
        Startup(GuiceViewManager manager) {
            manager.start();
        }
    }
}
