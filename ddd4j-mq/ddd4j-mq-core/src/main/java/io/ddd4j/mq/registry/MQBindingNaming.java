package io.ddd4j.mq.registry;


import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 绑定命名工具：将 topic + tag 语义映射为 camelCase binding / 函数 Bean 名。
 * <p>
 * 示例：{@code order.paid} + {@code notify} → {@code orderPaidNotify}
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class MQBindingNaming {

    private MQBindingNaming() {
    }

    /**
     * 由 topic 与 tag 生成 camelCase binding 名。
     *
     * @param topic 主题，如 {@code order.paid}
     * @param tag   标签，如 {@code notify}；通配符 {@code *} 时不追加 tag 段
     * @return binding 名
     */
    public static String bindingName(String topic, String tag) {
        String base = toCamelCase(normalizeTopic(topic));
        if (!hasText(tag) || "*".equals(tag.trim())) {
            return io.ddd4j.kit.lang.StrKit.isEmpty(base) ? "default" : base;
        }
        String tagPart = toCamelCase(normalizeTag(tag));
        if (io.ddd4j.kit.lang.StrKit.isEmpty(tagPart)) {
            return io.ddd4j.kit.lang.StrKit.isEmpty(base) ? "default" : base;
        }
        return base + capitalize(tagPart);
    }

    /**
     * 生成 Spring Cloud Stream 入站 binding 名。
     *
     * @param bindingName 函数 Bean / binding 基名
     * @return 如 {@code orderPaidNotify-in-0}
     */
    public static String inboundBindingName(String bindingName) {
        return bindingName + "-in-0";
    }

    /**
     * 生成 Spring Cloud Stream 出站 binding 名。
     *
     * @param bindingName 函数 Bean / binding 基名
     * @return 如 {@code orderPaidNotify-out-0}
     */
    public static String outboundBindingName(String bindingName) {
        return bindingName + "-out-0";
    }

    /**
     * 规范化 topic：去除首尾空白。
     */
    private static String normalizeTopic(String topic) {
        return java.util.Objects.isNull(topic) ? "" : topic.trim();
    }

    /**
     * 规范化 tag：复合表达式取首个 include（{@code A || B} → {@code A}）。
     */
    private static String normalizeTag(String tag) {
        return MQTagMatcher.findIncludes(tag).stream().findFirst().orElse("");
    }

    /**
     * 将 {@code order.paid}、{@code order-paid}、{@code order_paid} 转为 {@code orderPaid}。
     */
    private static String toCamelCase(String raw) {
        if (!hasText(raw)) {
            return "";
        }
        String[] parts = raw.split("[._\\-]+");
        if (parts.length == 0) {
            return "";
        }
        String head = parts[0].toLowerCase(Locale.ROOT);
        if (parts.length == 1) {
            return head;
        }
        return head + Arrays.stream(parts, 1, parts.length)
                .filter(MQBindingNaming::hasText)
                .map(MQBindingNaming::capitalize)
                .collect(Collectors.joining());
    }

    /**
     * 首字母大写，其余保持原样（已为小写时符合 camelCase 拼接规则）。
     */
    private static String capitalize(String segment) {
        if (!hasText(segment)) {
            return "";
        }
        String lower = segment.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static boolean hasText(String s) {
        return java.util.Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }
}
