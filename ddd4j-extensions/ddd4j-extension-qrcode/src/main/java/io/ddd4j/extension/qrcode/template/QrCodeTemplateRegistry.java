package io.ddd4j.extension.qrcode.template;

import java.util.Optional;

/**
 * Registry for reusable QR outer-frame templates.
 */
public interface QrCodeTemplateRegistry {

    /**
     * \u6ce8\u518c\u4e8c\u7ef4\u7801\u5916\u6846\u6a21\u677f\u3002
     * @param definition \u6a21\u677f\u5b9a\u4e49
     */
    void register(QrCodeTemplateDefinition definition);

    /**
     * \u6309 ID \u67e5\u627e\u5916\u6846\u6a21\u677f\u3002
     * @param id \u6a21\u677f ID
     * @return \u53ef\u9009\u6a21\u677f\u5b9a\u4e49
     */
    Optional<QrCodeTemplateDefinition> find(String id);

    /**
     * \u79fb\u9664\u5916\u6846\u6a21\u677f\u3002
     * @param id \u6a21\u677f ID
     * @return \u79fb\u9664\u6210\u529f\u65f6\u4e3a true
     */
    boolean remove(String id);
}
