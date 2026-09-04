package io.ddd4j.extension.qrcode.template;

import org.junit.jupiter.api.Test;


import java.util.Collections;

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

    @Test
    void bindTextWithoutMutatingRegisteredTemplate() {
        QrCodeFrame frame = QrCodeFrame.builder(320, 380)
                .addElement(QrCodeTextElement.builder("欢迎 ${name}")
                        .bounds(20, 10, 280, 40).build())
                .addElement(QrCodeBlockElement.builder().x(20).y(60).width(280).height(280).build())
                .build();
        QrCodeTemplateDefinition definition = new QrCodeTemplateDefinition("welcome", frame);

        QrCodeFrame bound = new QrCodeTemplateBinder().bind(definition,
                Collections.singletonMap("name", "DDD4J");

        assertThat(((QrCodeTextElement) bound.getElements().get(0)).getText()).isEqualTo("欢迎 DDD4J");
        assertThat(((QrCodeTextElement) definition.getFrame().getElements().get(0)).getText())
                .isEqualTo("欢迎 ${name}");
    }
}
