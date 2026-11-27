/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.extension.qrcode.template;

import org.junit.jupiter.api.Test;


import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** \u9a8c\u8bc1 <code>QrCodeTemplateRegistry</code> \u5728<code>bindText</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class QrCodeTemplateRegistryTests {

    /** \u9a8c\u8bc1<code>registry.find("label")</code> \u5305\u542b <code>definition</code>\uff0c<code>registry.remove("label")</code>\u4e3a\u771f\uff0c<code>registry.find("label")</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>((QrCodeTextElement) bound.getElements().get(0)).getText()</code> \u7b49\u4e8e <code>"\u6b22\u8fce DDD4J"</code>\uff0c<code>((QrCodeTextElement) definition.getFrame().getElements().get(0)).getText()</code> \u7b49\u4e8e <code>"\u6b22\u8fce ${name}"</code>\u3002 */
    @Test
    void bindTextWithoutMutatingRegisteredTemplate() {
        QrCodeFrame frame = QrCodeFrame.builder(320, 380)
                .addElement(QrCodeTextElement.builder("欢迎 ${name}")
                        .bounds(20, 10, 280, 40).build())
                .addElement(QrCodeBlockElement.builder().x(20).y(60).width(280).height(280).build())
                .build();
        QrCodeTemplateDefinition definition = new QrCodeTemplateDefinition("welcome", frame);

        QrCodeFrame bound = new QrCodeTemplateBinder().bind(definition,
                Collections.singletonMap("name", "DDD4J"));

        assertThat(((QrCodeTextElement) bound.getElements().get(0)).getText()).isEqualTo("欢迎 DDD4J");
        assertThat(((QrCodeTextElement) definition.getFrame().getElements().get(0)).getText())
                .isEqualTo("欢迎 ${name}");
    }
}
