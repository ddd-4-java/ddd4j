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
    /** \u7531\u5f53\u524d\u8fd0\u884c\u73af\u5883\u7ba1\u7406\u548c\u8c03\u5ea6\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    private final List<? extends ProjectionView<Object>> views;

    /**
     * 选择需要运行的视图。
     *
     * @param views 业务投影视图
     */
    public Ddd4jProjectionGuiceModule(Collection<? extends ProjectionView<Object>> views) {
        this.views = List.copyOf(views);
    }

    /** \u4e3a Guice \u88c5\u914d\u6295\u5f71\u670d\u52a1\u3001\u89c6\u56fe\u7ba1\u7406\u5668\u53ca\u6846\u67b6\u542f\u52a8\u4efb\u52a1\u3002 */
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
     * \u7528\u4e8b\u4ef6\u4f4d\u70b9\u4ed3\u50a8\u88c5\u914d\u5171\u4eab\u6295\u5f71\u5904\u7406\u670d\u52a1\u3002
     * @param repository \u4fdd\u5b58\u5e76\u8bfb\u53d6\u6295\u5f71\u68c0\u67e5\u70b9\u7684\u4ed3\u50a8
     * @return \u6295\u5f71\u5904\u7406\u670d\u52a1\u5b9e\u4f8b
     */
    @Provides
    @Singleton
    ProjectionService projectionService(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    /**
     * \u5c06\u4e8b\u4ef6\u8bfb\u53d6\u7aef\u53e3\u4e0e\u5171\u4eab\u6295\u5f71\u6267\u884c\u670d\u52a1\u88c5\u914d\u4e3a\u8fd0\u884c\u5668\u3002
     * @param service \u8d1f\u8d23\u6295\u5f71\u4e8b\u52a1\u53ca\u540c\u6b65\u4f4d\u70b9\u7684\u670d\u52a1\u7ec4\u4ef6
     * @param reader \u4ece\u4e8b\u4ef6\u6e90\u6309\u4f4d\u70b9\u5206\u6279\u8bfb\u53d6\u4e8b\u4ef6\u7684\u7ec4\u4ef6
     * @return \u6267\u884c\u5df2\u6ce8\u518c\u6295\u5f71\u89c6\u56fe\u7684\u6295\u5f71\u8fd0\u884c\u5668
     */
    @Provides
    @Singleton
    ProjectionRunner<Object> projectionRunner(ProjectionService service, EventChunkReader<Object> reader) {
        return new ProjectionRunner<>(service, reader);
    }

    /**
     * \u4ee5 Guice \u751f\u547d\u5468\u671f\u7ec4\u4ef6\u88c5\u914d\u6295\u5f71\u89c6\u56fe\u7ba1\u7406\u5668\u3002
     * @param repository \u4fdd\u5b58\u5e76\u8bfb\u53d6\u6295\u5f71\u68c0\u67e5\u70b9\u7684\u4ed3\u50a8
     * @param runner \u8c03\u7528\u89c6\u56fe\u5e76\u7ef4\u62a4\u4e8b\u4ef6\u4f4d\u70b9\u7684\u6295\u5f71\u8fd0\u884c\u5668
     * @return \u5f53\u524d\u5e94\u7528\u7684 Guice \u89c6\u56fe\u7ba1\u7406\u5668
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
         * \u53d6\u5f97 Guice \u6295\u5f71\u89c6\u56fe\u7ba1\u7406\u5668\u5e76\u5173\u8054\u542f\u52a8\u4efb\u52a1\u3002
         * @param manager \u7528\u4e8e\u8c03\u5ea6\u5f53\u524d\u89c6\u56fe\u7684\u7ba1\u7406\u5668
         */
        @Inject
        Startup(GuiceViewManager manager) {
            manager.start();
        }
    }
}
