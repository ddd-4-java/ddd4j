package io.ddd4j.spring.config;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.spring.command.SpringCommandBus;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 显式命令启用入口。通过 {@code @Import(SpringCommandConfig.class)} 使用；
 * 在用户配置完成后复用已有总线，否则创建运行时总线，不激活存储或投影。
 */
public final class SpringCommandConfig implements DeferredImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[]{CommandConfiguration.class.getName()};
    }

    /**
     * 延迟装配使既有扫描和用户的 Bean 定义优先。
     */
    public static class CommandConfiguration {
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
        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getBeanFactory().getBeanNamesForType(CommandBus.class, true, false).length == 0;
        }
    }
}
