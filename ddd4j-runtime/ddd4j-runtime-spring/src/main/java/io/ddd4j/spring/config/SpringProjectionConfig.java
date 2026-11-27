package io.ddd4j.spring.config;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.data.projection.spring.SpringProjectionScheduler;
import io.ddd4j.data.projection.spring.SpringProjectionViewManager;
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
     * \u9009\u62e9\u5f53\u524d\u914d\u7f6e\u5bfc\u5165\u7684 Spring \u7ec4\u4ef6\u5b9a\u4e49\u3002
     * @param metadata \u6846\u67b6\u4f20\u5165\u7684\u6ce8\u89e3\u5143\u6570\u636e
     * @return \u9700\u8981\u7531 Spring \u5bfc\u5165\u7684\u914d\u7f6e\u7c7b\u540d\u6570\u7ec4
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
         * \u9009\u62e9\u8bc4\u4f30\u7f3a\u5931 Bean \u6761\u4ef6\u7684\u914d\u7f6e\u9636\u6bb5\u3002
         * @return Spring \u6ce8\u518c\u914d\u7f6e Bean \u65f6\u8bc4\u4f30\u7684\u9636\u6bb5
         */
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        /**
         * \u68c0\u67e5\u5f53\u524d\u5bb9\u5668\u662f\u5426\u9700\u8981\u6ce8\u518c\u76f8\u5e94\u7684\u9ed8\u8ba4\u7ec4\u4ef6\u3002
         * @param context \u5f53\u524d\u6846\u67b6\u4f20\u5165\u7684\u6267\u884c\u6216\u5bb9\u5668\u4e0a\u4e0b\u6587
         * @param metadata \u6846\u67b6\u4f20\u5165\u7684\u6ce8\u89e3\u5143\u6570\u636e
         * @return \u6ee1\u8db3\u9ed8\u8ba4\u7ec4\u4ef6\u7684\u6ce8\u518c\u6761\u4ef6\u65f6\u8fd4\u56de true
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
         * \u9009\u62e9\u8bc4\u4f30\u7f3a\u5931 Bean \u6761\u4ef6\u7684\u914d\u7f6e\u9636\u6bb5\u3002
         * @return Spring \u6ce8\u518c\u914d\u7f6e Bean \u65f6\u8bc4\u4f30\u7684\u9636\u6bb5
         */
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        /**
         * \u68c0\u67e5\u5f53\u524d\u5bb9\u5668\u662f\u5426\u9700\u8981\u6ce8\u518c\u76f8\u5e94\u7684\u9ed8\u8ba4\u7ec4\u4ef6\u3002
         * @param context \u5f53\u524d\u6846\u67b6\u4f20\u5165\u7684\u6267\u884c\u6216\u5bb9\u5668\u4e0a\u4e0b\u6587
         * @param metadata \u6846\u67b6\u4f20\u5165\u7684\u6ce8\u89e3\u5143\u6570\u636e
         * @return \u6ee1\u8db3\u9ed8\u8ba4\u7ec4\u4ef6\u7684\u6ce8\u518c\u6761\u4ef6\u65f6\u8fd4\u56de true
         */
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getBeanFactory().getBeanNamesForType(ViewManager.class, true, false).length == 0;
        }
    }
}
