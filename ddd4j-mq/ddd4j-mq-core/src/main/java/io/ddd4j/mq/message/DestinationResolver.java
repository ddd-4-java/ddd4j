package io.ddd4j.mq.message;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;

import java.util.Objects;

/**
 * MQ 目的地统一解析器。
 *
 * <p>收口各 Publisher 中重复的 namespace/topic/tag/separator 拼接逻辑，
 * 所有 Broker 的 Publisher 只需调用本类两个方法即可完成元数据补齐和物理地址解析。
 *
 * <p>优先级规则（高 → 低）：
 * <ol>
 *   <li>destination 字段（显式指定的）</li>
 *   <li>event 字段（MQEvent 上的）</li>
 *   <li>properties 默认值（配置文件的）</li>
 * </ol>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class DestinationResolver {

    private DestinationResolver() {
    }

    /**
     * 补齐 event 元数据（topic / namespace / msgId），所有 Publisher 共享。
     *
     * @param event  MQ 事件
     * @param props  MQ 配置
     */
    public static void fillDefaults(MQEvent event, MQProperties props) {
        if (Objects.isNull(event)) {
            return;
        }
        if (!StrKit.hasText(event.getTopic())) {
            event.setTopic(props.getDefaultTopic());
        }
        if (!StrKit.hasText(event.getNamespace())) {
            event.setNamespace(props.getNamespace());
        }
        if (Objects.isNull(event.getMsgId())) {
            event.setMsgId(String.valueOf(System.currentTimeMillis()));
        }
    }

    /**
     * 解析最终物理地址。
     *
     * <p>拼接规则：{@code [namespace][separator]topic[separator]tag}
     * <p>separator 默认 {@code .}，可被 event.separator 覆盖。
     *
     * @param event       MQ 事件
     * @param destination 目的地（可为 null，从 event 推断）
     * @param props       MQ 配置
     * @return 物理地址字符串
     */
    public static String resolvePhysicalAddress(MQEvent event, Destination destination, MQProperties props) {
        String namespace = firstNonBlank(
                destination != null ? destination.getNamespace() : null,
                event != null ? event.getNamespace() : null,
                props.getNamespace());
        String topic = firstNonBlank(
                destination != null ? destination.getTopic() : null,
                event != null ? event.getTopic() : null,
                props.getDefaultTopic());
        String tag = firstNonBlank(
                destination != null ? destination.getTag() : null,
                event != null ? event.getTag() : null);
        String separator = ".";

        String base = StrKit.hasText(namespace) ? namespace + separator + topic : topic;
        return StrKit.hasText(tag) ? base + separator + tag : base;
    }

    /**
     * 返回首个非空白字符串，全空时返回 null。
     */
    private static String firstNonBlank(String... values) {
        if (Objects.isNull(values)) {
            return null;
        }
        for (String v : values) {
            if (StrKit.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
