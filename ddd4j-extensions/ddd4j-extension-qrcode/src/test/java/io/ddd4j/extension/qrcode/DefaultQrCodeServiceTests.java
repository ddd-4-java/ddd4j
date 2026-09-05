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
package io.ddd4j.extension.qrcode;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.ddd4j.extension.qrcode.batch.QrCodeBatchItem;
import io.ddd4j.extension.qrcode.batch.QrCodeBatchResult;
import io.ddd4j.extension.qrcode.command.DecodeQrCodeCommand;
import io.ddd4j.extension.qrcode.command.GenerateQrCodeCommand;
import io.ddd4j.extension.qrcode.result.QrCodeArtifact;
import io.ddd4j.extension.qrcode.result.QrCodeScanResult;
import io.ddd4j.extension.qrcode.model.QrCodeDecodeRequest;
import io.ddd4j.extension.qrcode.model.QrCodeRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultQrCodeServiceTests {

    @Test
    void generateAndDecodePreserveCorrelationMetadata() {
        try (DefaultQrCodeService service = new DefaultQrCodeService()) {
            QrCodeArtifact artifact = service.generate(GenerateQrCodeCommand.builder()
                    .correlationId("correlation-1")
                    .templateId("plain")
                    .request(QrCodeRequest.builder().content("ddd4j-qrcode").build())
                    .build());

            QrCodeScanResult scan = service.decode(DecodeQrCodeCommand.builder()
                    .correlationId("correlation-2")
                    .request(QrCodeDecodeRequest.from(artifact.getOutput().getBytes()))
                    .build());

            assertThat(artifact.getCorrelationId()).isEqualTo("correlation-1");
            assertThat(scan.getCorrelationId()).isEqualTo("correlation-2");
            assertThat(scan.getResults()).extracting(result -> result.getText()).containsExactly("ddd4j-qrcode");
        }
    }

    @Test
    void batchIsOrderedAndNonAtomic() {
        try (DefaultQrCodeService service = new DefaultQrCodeService()) {
            List<QrCodeBatchItem> items = new ArrayList<QrCodeBatchItem>();
            for (int index = 0; index < 100; index++) {
                GenerateQrCodeCommand command = index == 50 ? null : GenerateQrCodeCommand.builder()
                        .correlationId("correlation-" + index)
                        .request(QrCodeRequest.builder().content("item-" + index).build())
                        .build();
                items.add(QrCodeBatchItem.builder().itemId("item-" + index).command(command).build());
            }

            QrCodeBatchResult result = service.generateBatch(items);

            assertThat(result.getItems()).hasSize(100);
            assertThat(result.successCount()).isEqualTo(99);
            assertThat(result.failureCount()).isEqualTo(1);
            assertThat(result.getItems().get(0).getItemId()).isEqualTo("item-0");
            assertThat(result.getItems().get(50).isSuccess()).isFalse();
            assertThat(result.getItems().get(99).getItemId()).isEqualTo("item-99");
        }
    }
}
