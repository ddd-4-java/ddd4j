package io.ddd4j.mq.listener;

import io.ddd4j.mq.annotation.EventListener;
import lombok.Builder;
import lombok.Getter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@link EventListener} 解析后的监听器定义。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
@Builder
public class ListenerDefinition {

    private final Object bean;
    /**
     * Spring Bean 名称（BeanPostProcessor 阶段登记）。
     */
    private final String beanName;
    private final Method method;
    private final String group;
    private final String namespace;
    private final String topic;
    private final String tags;
    private final List<String> supports;
    private final String separator;

    /**
     * 从注解与方法元数据构建监听器定义。
     *
     * @param bean   目标 Bean
     * @param method 监听方法
     * @param ann    注解实例
     * @return 监听器定义
     */
    public static ListenerDefinition from(Object bean, Method method, EventListener ann) {
        return ListenerDefinition.builder()
                .bean(bean)
                .method(method)
                .group(ann.group())
                .namespace(ann.namespace())
                .topic(ann.topic())
                .tags(ann.tags())
                .supports(Arrays.asList(ann.supports()))
                .separator(ann.separator())
                .build();
    }

    /**
     * BFPP 阶段构建监听器定义（Bean 实例尚未创建）。
     *
     * @param beanName         Spring Bean 名称
     * @param method           监听方法
     * @param ann              注解实例
     * @param defaultGroup     默认消费组前缀（通常为 spring.application.name）
     * @param defaultNamespace 默认命名空间（通常为 ddd4j.mq.namespace）
     * @return 监听器定义
     */
    public static ListenerDefinition from(
            String beanName,
            Method method,
            EventListener ann,
            String defaultGroup,
            String defaultNamespace) {

        String group = Objects.nonNull(ann.group()) && !io.ddd4j.kit.lang.StrKit.isBlank(ann.group())
                ? ann.group()
                : defaultGroup + "_" + method.getName();
        String namespace = Objects.nonNull(ann.namespace()) && !io.ddd4j.kit.lang.StrKit.isBlank(ann.namespace())
                ? ann.namespace()
                : defaultNamespace;

        return ListenerDefinition.builder()
                .beanName(beanName)
                .method(method)
                .group(group)
                .namespace(namespace)
                .topic(ann.topic())
                .tags(ann.tags())
                .supports(Arrays.asList(ann.supports()))
                .separator(ann.separator())
                .build();
    }

    /**
     * 返回策略匹配支持列表（不可变）。
     */
    public List<String> supports() {
        return Objects.isNull(supports) ? Collections.emptyList() : Collections.unmodifiableList(supports);
    }

    /**
     * 返回绑定名（topic + tag 的 camelCase 命名）。
     */
    public String bindingName() {
        return BindingNaming.bindingName(topic, tags);
    }

    /**
     * {@link #bindingName()} 别名。
     */
    public String bindingKey() {
        return bindingName();
    }

    /**
     * 物理 destination（namespace.topic）。
     */
    public String physicalDestination() {
        if (Objects.nonNull(namespace) && !io.ddd4j.kit.lang.StrKit.isBlank(namespace)) {
            return namespace + "." + topic;
        }
        return topic;
    }
}
