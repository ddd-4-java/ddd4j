package io.ddd4j.spring.config;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.spring.projection.SpringProjectionScheduler;
import io.ddd4j.spring.projection.SpringProjectionViewManager;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.scheduling.TaskScheduler;

import java.util.Collection;

/**
 * 显式投影启用入口。业务必须提供 TaskScheduler、ProjectionRunner 及真实位置/事件读取配置；
 * 不创建内存仓储、事件源或线程池。已有旧入口或用户管理器时复用它。
 */
public final class SpringProjectionConfig implements DeferredImportSelector {
    /**
     * 选择当前配置导入的 Spring 组件定义。
     *
     * @param metadata 框架传入的注解元数据
     * @return 需要由 Spring 导入的配置类名数组
     */
    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[]{ProjectionConfiguration.class.getName()};
    }

    /**
     * 用户 Bean 定义之后装配投影平台适配器。
     */
    @Conditional(MissingManager.class)
    public static class ProjectionConfiguration {
        /**
         * 创建平台调度适配器。
         *
         * @param taskScheduler 业务管理生命周期的 Spring 调度器
         * @return Spring 原生 cron 调度适配器
         */
        @Bean
        @Conditional(MissingScheduler.class)
        public SpringProjectionScheduler springProjectionScheduler(TaskScheduler taskScheduler) {
            return new SpringProjectionScheduler(taskScheduler);
        }

        /**
         * 创建共享运行器的生命周期适配器。
         *
         * @param scheduler 平台调度器
         * @param runner    业务显式配置的共享运行器
         * @param views     业务视图
         * @return 自动启动的视图管理器
         */
        @Bean
        @Conditional(MissingManager.class)
        public SpringProjectionViewManager springProjectionViewManager(ViewScheduler scheduler,
                                                                       ProjectionRunner<?> runner, Collection<ProjectionView<?>> views) {
            return new SpringProjectionViewManager(scheduler, runner, views);
        }
    }

    /**
     * 在 Bean 注册阶段检查平台调度器。
     */
    public static final class MissingScheduler implements ConfigurationCondition {
        /**
         * 选择评估缺失 Bean 条件的配置阶段。
         *
         * @return Spring 注册配置 Bean 时评估的阶段
         */
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        /**
         * 检查当前容器是否需要注册相应的默认组件。
         *
         * @param context  当前框架传入的执行或容器上下文
         * @param metadata 框架传入的注解元数据
         * @return 满足默认组件的注册条件时返回 true
         */
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getBeanFactory().getBeanNamesForType(ViewScheduler.class, true, false).length == 0;
        }
    }

    /**
     * 在 Bean 注册阶段检查已有视图管理器。
     */
    public static final class MissingManager implements ConfigurationCondition {
        /**
         * 选择评估缺失 Bean 条件的配置阶段。
         *
         * @return Spring 注册配置 Bean 时评估的阶段
         */
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        /**
         * 检查当前容器是否需要注册相应的默认组件。
         *
         * @param context  当前框架传入的执行或容器上下文
         * @param metadata 框架传入的注解元数据
         * @return 满足默认组件的注册条件时返回 true
         */
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getBeanFactory().getBeanNamesForType(ViewManager.class, true, false).length == 0;
        }
    }
}
