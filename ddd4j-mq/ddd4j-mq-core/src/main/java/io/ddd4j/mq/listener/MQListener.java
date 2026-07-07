package io.ddd4j.mq.listener;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * MQ 监听器定义（100% 对齐 base-mq {@code MQListener}）。
 *
 * <p>由框架适配层（如 ddd4j-mq-spring 的 BeanPostProcessor）在容器初始化阶段构建，
 * 供 {@link MQClient#initConsumer} 建立原生消费者，
 * {@link MQClient#consume} 反射调用目标方法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MQListener {

    /** 目标 Bean */
    private Object bean;
    /** 监听方法 */
    private Method method;
    /** 消费者组，默认 {@code ${应用名}_${方法名}} */
    private String group;
    /** 命名空间，默认全局 namespace */
    private String namespace;
    /** 主题 */
    private String topic;
    /** 标签表达式（支持 {@code *} / {@code A || B} / {@code * -C}） */
    private String tags;
    /** 策略过滤列表，配合 {@link MQEvent#supports(List)} */
    private List<String> supports;
    /** namespace/topic/tag 拼接符，为空时由各 broker 决定默认值 */
    private String concat;

    /**
     * 从注解与方法元数据构建监听器定义。
     */
    public static MQListener of(Object bean, Method method, MQEventListener ann) {
        return MQListener.builder()
                .bean(bean)
                .method(method)
                .group(ann.group())
                .namespace(ann.namespace())
                .topic(ann.topic())
                .tags(ann.tags())
                .supports(Arrays.asList(ann.supports()))
                .concat(ann.separator())
                .build();
    }

    /**
     * 返回监听方法首个参数类型（约定为 {@link MQEvent} 子类），用于反序列化。
     */
    @SuppressWarnings("unchecked")
    public Class<? extends MQEvent> payloadType() {
        if (Objects.isNull(method) || method.getParameterCount() == 0) {
            return MQEvent.class;
        }
        Class<?> first = method.getParameterTypes()[0];
        return MQEvent.class.isAssignableFrom(first) ? (Class<? extends MQEvent>) first : MQEvent.class;
    }

    /**
     * 返回策略匹配支持列表。
     */
    public List<String> supports() {
        return Objects.isNull(supports) ? List.of() : supports;
    }

    /**
     * 拼接 {@code namespace[concat]topic[concat]tags}（对齐 base-mq）。
     */
    public String namespaceTopicTags() {
        String sep = Objects.isNull(concat) || concat.isEmpty() ? "." : concat;
        String ns = Objects.isNull(namespace) ? "" : namespace;
        String tp = Objects.isNull(topic) ? "" : topic;
        String tg = Objects.isNull(tags) || tags.isEmpty() ? "" : sep + tags;
        return ns + sep + tp + tg;
    }

    /**
     * 带命名空间前缀的拼接便捷方法（对齐 base-mq）。
     */
    public String namespace(String concat) {
        String sep = Objects.isNull(concat) || concat.isEmpty() ? this.concat : concat;
        if (Objects.nonNull(namespace) && !namespace.isEmpty()) {
            return namespace + sep;
        }
        return "";
    }

    /**
     * 消费者组拼接（对齐 base-mq）。
     */
    public String group(String concat) {
        String sep = Objects.isNull(concat) || concat.isEmpty() ? this.concat : concat;
        if (Objects.nonNull(group) && !group.isEmpty()) {
            return group + sep;
        }
        return "";
    }
}
