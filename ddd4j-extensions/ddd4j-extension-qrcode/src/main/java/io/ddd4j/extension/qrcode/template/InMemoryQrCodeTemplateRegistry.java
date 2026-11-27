package io.ddd4j.extension.qrcode.template;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;

/**
 * Thread-safe in-memory QR template registry.
 */
public class InMemoryQrCodeTemplateRegistry implements QrCodeTemplateRegistry {

    /** \u6309\u6a21\u677f\u6807\u8bc6\u7d22\u5f15\u5df2\u6ce8\u518c\u7684\u4e8c\u7ef4\u7801\u6a21\u677f\u3002 */
    private final ConcurrentMap<String, QrCodeTemplateDefinition> templates =
            new ConcurrentHashMap<String, QrCodeTemplateDefinition>();

    /**
     * 注册二维码外框模板。
     *
     * <p>此实现调用 <code>templates.put(definition.getId(), definition)</code>。</p>
     *
     * @param definition 模板定义
     */
    @Override
    public void register(QrCodeTemplateDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        templates.put(definition.getId(), definition);
    }

    /**
     * 查找与给定条件对应的条目。
     *
     * <p>此实现调用 <code>StringUtils.isBlank(id)</code>。</p>
     *
     * @param id 模板 ID
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.ofNullable(templates.get(id))</code>
     */
    @Override
    public Optional<QrCodeTemplateDefinition> find(String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(templates.get(id));
    }

    /**
     * 移除外框模板。
     *
     * <p>此实现调用 <code>StringUtils.isNotBlank(id)</code>。</p>
     *
     * @param id 模板 ID
     * @return 实际返回<code>StringUtils.isNotBlank(id) &amp;&amp; Objects.nonNull(templates.remove(id))</code>
     */
    @Override
    public boolean remove(String id) {
        return StringUtils.isNotBlank(id) && Objects.nonNull(templates.remove(id));
    }
}
