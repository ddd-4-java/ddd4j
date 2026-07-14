package io.ddd4j.extension.qrcode.template;

import org.junit.jupiter.api.Test;

import io.github.hiwepy.zxing.frame.QrCodeBlockElement;
import io.github.hiwepy.zxing.frame.QrCodeFrame;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeTemplateRegistryTests {

    @Test
    void registerFindAndRemoveTemplate() {
        QrCodeTemplateRegistry registry = new InMemoryQrCodeTemplateRegistry();
        QrCodeTemplateDefinition definition = new QrCodeTemplateDefinition("label",
                QrCodeFrame.builder(320, 400)
                        .addElement(QrCodeBlockElement.builder().x(20).y(60).width(280).height(280).build())
                        .build());

        registry.register(definition);

        assertThat(registry.find("label")).contains(definition);
        assertThat(registry.remove("label")).isTrue();
        assertThat(registry.find("label")).isEmpty();
    }
}
