package io.ddd4j.spring.config;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.spring.command.SpringCommandBus;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 显式命令启用入口。通过 {@code @Import(SpringCommandConfig.class)} 使用；
 * 在用户配置完成后复用已有总线，否则创建运行时总线，不激活存储或投影。
 */
public final class SpringCommandConfig implements DeferredImportSelector {
    /** 创建显式命令配置的延迟导入器。 */
    public SpringCommandConfig() {
    }

    /**
     * 选择当前配置导入的 Spring 组件定义。
     *
     * @param metadata 框架传入的注解元数据
     * @return 需要由 Spring 导入的配置类名数组
     */
    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[]{CommandConfiguration.class.getName()};
    }

    /**
     * 延迟装配使既有扫描和用户的 Bean 定义优先。
     */
    public static class CommandConfiguration {
        /** 供 Spring 注册默认命令总线。 */
        public CommandConfiguration() {
        }

        /**
         * 创建命令总线，保持既有 Bean 名。
         *
         * @param context 容器上下文
         * @return 延迟发现执行器的总线
         */
        @Bean
        @Conditional(MissingCommandBus.class)
        public SpringCommandBus springCommandBus(ApplicationContext context) {
            return new SpringCommandBus(context);
        }
    }

    /**
     * 只判断已声明的 Bean 类型，不实例化总线或执行器。
     */
    public static final class MissingCommandBus implements ConfigurationCondition {
        /** 创建命令总线缺失条件。 */
        public MissingCommandBus() {
        }

        /**
         * 选择评估缺失 Bean 条件的配置阶段。
         *
         * @return Spring 注册配置 Bean 时评估的阶段
         */
        @Override
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
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getBeanFactory().getBeanNamesForType(CommandBus.class, true, false).length == 0;
        }
    }
}
