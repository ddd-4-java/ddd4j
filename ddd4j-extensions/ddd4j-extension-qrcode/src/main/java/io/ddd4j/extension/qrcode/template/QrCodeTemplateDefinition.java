package io.ddd4j.extension.qrcode.template;

import org.apache.commons.lang3.StringUtils;

import io.github.hiwepy.zxing.frame.QrCodeFrame;
import lombok.Getter;

/** Named reusable outer-frame template. */
@Getter
public final class QrCodeTemplateDefinition {

    private final String id;
    private final QrCodeFrame frame;

    public QrCodeTemplateDefinition(String id, QrCodeFrame frame) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("template id must not be blank");
        }
        this.id = id;
        this.frame = java.util.Objects.requireNonNull(frame, "frame must not be null");
    }
}
