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
    /**
     * \u9009\u62e9\u5f53\u524d\u914d\u7f6e\u5bfc\u5165\u7684 Spring \u7ec4\u4ef6\u5b9a\u4e49\u3002
     * @param metadata \u6846\u67b6\u4f20\u5165\u7684\u6ce8\u89e3\u5143\u6570\u636e
     * @return \u9700\u8981\u7531 Spring \u5bfc\u5165\u7684\u914d\u7f6e\u7c7b\u540d\u6570\u7ec4
     */
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
        /**
         * \u9009\u62e9\u8bc4\u4f30\u7f3a\u5931 Bean \u6761\u4ef6\u7684\u914d\u7f6e\u9636\u6bb5\u3002
         * @return Spring \u6ce8\u518c\u914d\u7f6e Bean \u65f6\u8bc4\u4f30\u7684\u9636\u6bb5
         */
        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        /**
         * \u68c0\u67e5\u5f53\u524d\u5bb9\u5668\u662f\u5426\u9700\u8981\u6ce8\u518c\u76f8\u5e94\u7684\u9ed8\u8ba4\u7ec4\u4ef6\u3002
         * @param context \u5f53\u524d\u6846\u67b6\u4f20\u5165\u7684\u6267\u884c\u6216\u5bb9\u5668\u4e0a\u4e0b\u6587
         * @param metadata \u6846\u67b6\u4f20\u5165\u7684\u6ce8\u89e3\u5143\u6570\u636e
         * @return \u6ee1\u8db3\u9ed8\u8ba4\u7ec4\u4ef6\u7684\u6ce8\u518c\u6761\u4ef6\u65f6\u8fd4\u56de true
         */
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getBeanFactory().getBeanNamesForType(CommandBus.class, true, false).length == 0;
        }
    }
}
