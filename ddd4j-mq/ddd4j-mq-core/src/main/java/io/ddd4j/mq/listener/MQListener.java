package io.ddd4j.mq.listener;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.util.TagMatcher;
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
@SuppressWarnings("unchecked")
public class MQListener {

    /**
     * 目标 Bean
     */
    private Object bean;
    /**
     * 监听方法
     */
    private Method method;
    /**
     * 消费者组，默认 {@code ${应用名}_${方法名}}
     */
    private String group;
    /**
     * 命名空间，默认全局 namespace
     */
    private String namespace;
    /**
     * 主题
     */
    private String topic;
    /**
     * 标签表达式（支持 {@code *} / {@code A || B} / {@code * -C}）
     */
    private String tags;
    /**
     * 策略过滤列表，配合 {@link MQEvent#supports(List)}
     */
    private List<String> supports;
    /**
     * namespace/topic/tag 拼接符，为空时由各 broker 决定默认值
     */
    private String separator;

    /**
     * \u4ece\u6ce8\u89e3\u4e0e\u65b9\u6cd5\u5143\u6570\u636e\u6784\u5efa\u76d1\u542c\u5668\u5b9a\u4e49\u3002
     *
     * @param bean \u76ee\u6807 Bean
     * @param method \u88ab {@link MQEventListener} \u6807\u6ce8\u7684\u76d1\u542c\u65b9\u6cd5
     * @param ann \u65b9\u6cd5\u4e0a\u7684 {@link MQEventListener} \u6ce8\u89e3
     * @return \u6784\u5efa\u5b8c\u6210\u7684\u76d1\u542c\u5668\u5b9a\u4e49
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
                .separator(ann.separator())
                .build();
    }

    /**
     * \u8fd4\u56de\u76d1\u542c\u65b9\u6cd5\u9996\u4e2a\u53c2\u6570\u7c7b\u578b\uff08\u7ea6\u5b9a\u4e3a {@link MQEvent} \u5b50\u7c7b\uff09\uff0c\u7528\u4e8e\u53cd\u5e8f\u5217\u5316\u3002
     *
     * @return \u6d88\u606f\u8f7d\u8377\u7c7b\u578b\uff0c\u65b9\u6cd5\u65e0\u53c2\u6570\u6216\u9996\u53c2\u975e {@link MQEvent} \u65f6\u8fd4\u56de {@code MQEvent.class}
     */
    public Class<? extends MQEvent> payloadType() {
        if (Objects.isNull(method) || method.getParameterCount() == 0) {
            return MQEvent.class;
        }
        Class<?> first = method.getParameterTypes()[0];
        return MQEvent.class.isAssignableFrom(first) ? (Class<? extends MQEvent>) first : MQEvent.class;
    }

    /**
     * \u8fd4\u56de\u7b56\u7565\u5339\u914d\u652f\u6301\u5217\u8868\u3002
     *
     * @return \u652f\u6301\u5217\u8868\uff0c\u672a\u914d\u7f6e\u65f6\u8fd4\u56de\u4e0d\u5339\u914d\u4efb\u4f55\u7b56\u7565\u7684\u4e0d\u53ef\u4fee\u6539\u7a7a\u5217\u8868
     */
    public List<String> supports() {
        return Objects.isNull(supports) ? List.of() : supports;
    }

    /**
     * \u7269\u7406\u8def\u7531\u952e\uff1a{@code namespace.topic[.tag]}\uff08\u4e0e ddd4j Disruptor
     *  {@code DisruptorEvent.getRouteExpression()} \u7edf\u4e00\u89c4\u5219\uff09\u3002
     *
     *  <h3>\u8def\u7531\u6a21\u578b</h3>
     *  <pre>
     *    namespace.topic.tag
     *    \u2514\u2500\u2500\u2500\u252c\u2500\u2500\u2500\u2518 \u2514\u2500\u252c\u2500\u2518 \u2514\u252c\u2518
     *     \u73af\u5883\u9694\u79bb  \u4e1a\u52a1\u5206\u7c7b  \u7ec6\u5206\u6807\u7b7e
     *  </pre>
     *  <ul>
     *    <li>{@code namespace} \u2014\u2014 \u547d\u540d\u7a7a\u95f4\uff0c\u7528\u4e8e\u591a\u73af\u5883 / \u591a\u79df\u6237\u9694\u79bb</li>
     *    <li>{@code topic} \u2014\u2014 \u6d88\u8d39\u7ebf\u7a0b\u9694\u79bb\u7ef4\u5ea6\uff0c\u4e0d\u540c topic \u8d70\u4e0d\u540c\u6d88\u8d39\u7ebf\u7a0b\u6c60</li>
     *    <li>{@code tag} \u2014\u2014 \u540c topic \u4e0b\u5171\u4eab\u6d88\u8d39\u7ebf\u7a0b\uff0c\u505a\u6d88\u606f\u8fc7\u6ee4</li>
     *  </ul>
     *
     *  <p>tag \u53d6 {@code tags} \u8868\u8fbe\u5f0f\u4e2d\u7b2c\u4e00\u4e2a\u6b63\u5411 include\uff08\u5982 {@code "paid || shipped"} \u2192 {@code "paid"}\uff09\uff0c
     *  \u4e0e event \u7aef\u5355 tag \u5bf9\u9f50\u3002tag \u4e3a null/\u7a7a/\u901a\u914d {@code "*"} \u65f6\u4e0d\u8ffd\u52a0\u7b2c\u4e09\u6bb5\u3002
     * @param separator \u62fc\u63a5\u7b26\uff08\u7531 {@link io.ddd4j.mq.MQClient#defaultConcat()} \u4f20\u5165\uff0c\u786e\u4fdd listener \u4e0e event \u540c\u89c4\u5219\uff09
     *
     * @return \u7269\u7406\u8def\u7531\u952e\u8868\u8fbe\u5f0f
     */
    public String getRouteExpression(String separator) {
        String sep = StrKit.isEmpty(separator) ? "." : separator;
        String ns = Objects.isNull(namespace) ? "" : namespace;
        String tp = Objects.isNull(topic) ? "" : topic;
        String base = ns + sep + tp;
        if (StrKit.isEmpty(tags) || "*".equals(tags.trim())) {
            return base;
        }
        String firstTag = TagMatcher.findIncludes(tags).stream().findFirst().orElse(null);
        if (StrKit.isEmpty(firstTag)) {
            return base;
        }
        return base + sep + firstTag;
    }

}
