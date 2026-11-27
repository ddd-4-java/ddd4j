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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import io.ddd4j.extension.qrcode.batch.QrCodeBatchItem;
import io.ddd4j.extension.qrcode.batch.QrCodeBatchItemResult;
import io.ddd4j.extension.qrcode.batch.QrCodeBatchResult;
import io.ddd4j.extension.qrcode.command.DecodeQrCodeCommand;
import io.ddd4j.extension.qrcode.command.GenerateQrCodeCommand;
import io.ddd4j.extension.qrcode.model.QrCodeDecodeResult;
import io.ddd4j.extension.qrcode.model.QrCodeOutput;
import io.ddd4j.extension.qrcode.result.QrCodeArtifact;
import io.ddd4j.extension.qrcode.result.QrCodeScanResult;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default QR code service with bounded, order-preserving batch execution.
 */
public class DefaultQrCodeService implements QrCodeService, AutoCloseable {

    /** \u9ed8\u8ba4\u5141\u8bb8\u7684\u6700\u5927\u6279\u91cf\u6570\u91cf\u3002 */
    public static final int DEFAULT_MAX_BATCH_SIZE = 100;

    /** \u8c03\u5ea6\u6279\u91cf\u4e8c\u7ef4\u7801\u751f\u6210\u4efb\u52a1\u7684\u6267\u884c\u5668\u3002 */
    private final ExecutorService executorService;
    /** \u5355\u4e2a\u6279\u91cf\u751f\u6210\u8bf7\u6c42\u5141\u8bb8\u5305\u542b\u7684\u6700\u5927\u9879\u76ee\u6570\u3002 */
    private final int maxBatchSize;

    /** \u4f7f\u7528\u9ed8\u8ba4\u5e76\u53d1\u5ea6\u548c\u6279\u91cf\u4e0a\u9650\u521b\u5efa\u4e8c\u7ef4\u7801\u670d\u52a1\u3002 */
    public DefaultQrCodeService() {
        this(Math.min(Runtime.getRuntime().availableProcessors(), 8), DEFAULT_MAX_BATCH_SIZE);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u5e76\u53d1\u5ea6\u548c\u6279\u91cf\u4e0a\u9650\u521b\u5efa\u4e8c\u7ef4\u7801\u670d\u52a1\u3002
     * @param concurrency \u5de5\u4f5c\u7ebf\u7a0b\u6570\u91cf
     * @param maxBatchSize \u6700\u5927\u6279\u91cf\u6570\u91cf
     */
    public DefaultQrCodeService(int concurrency, int maxBatchSize) {
        if (concurrency <= 0 || maxBatchSize <= 0) {
            throw new IllegalArgumentException("concurrency and maxBatchSize must be positive");
        }
        this.executorService = Executors.newFixedThreadPool(concurrency, daemonThreadFactory());
        this.maxBatchSize = maxBatchSize;
    }

    /** {@inheritDoc} */
    @Override
    public QrCodeArtifact generate(GenerateQrCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.getRequest(), "command.request must not be null");
        return QrCodeArtifact.builder()
                .correlationId(command.getCorrelationId())
                .templateId(command.getTemplateId())
                .output(encode(command.getRequest()))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public QrCodeScanResult decode(DecodeQrCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.getRequest(), "command.request must not be null");
        return new QrCodeScanResult(command.getCorrelationId(), decode(command.getRequest().getBytes()));
    }

    /** {@inheritDoc} */
    @Override
    public QrCodeBatchResult generateBatch(List<QrCodeBatchItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        if (items.size() > maxBatchSize) {
            throw new IllegalArgumentException("batch size must not exceed " + maxBatchSize);
        }
        List<Future<QrCodeBatchItemResult>> futures = new ArrayList<Future<QrCodeBatchItemResult>>(items.size());
        for (QrCodeBatchItem item : items) {
            futures.add(executorService.submit(createBatchTask(item)));
        }
        List<QrCodeBatchItemResult> results = new ArrayList<QrCodeBatchItemResult>(items.size());
        for (int index = 0; index < futures.size(); index++) {
            try {
                results.add(futures.get(index).get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                results.add(failure(items.get(index), "QRCODE_RENDER_FAILED",
                        "Batch execution was interrupted"));
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                results.add(failure(items.get(index), "QRCODE_RENDER_FAILED", cause.getMessage()));
            }
        }
        return new QrCodeBatchResult(results);
    }

    /**
     * \u6784\u9020\u6279\u91cf\u9879\u76ee\u7684\u6267\u884c\u4efb\u52a1\uff0c\u5728\u8fd0\u884c\u65f6\u6821\u9a8c\u9879\u76ee\u6807\u8bc6\u5e76\u751f\u6210\u4e8c\u7ef4\u7801\u3002
     * @param item \u5f85\u751f\u6210\u4e8c\u7ef4\u7801\u7684\u6279\u91cf\u9879\u76ee
     * @return \u4ea7\u751f\u8be5\u9879\u76ee\u6210\u529f\u7ed3\u679c\u7684\u53ef\u8c03\u5ea6\u4efb\u52a1
     */
    private Callable<QrCodeBatchItemResult> createBatchTask(final QrCodeBatchItem item) {
        return () -> {
            Objects.requireNonNull(item, "batch item must not be null");
            if (StringUtils.isBlank(item.getItemId())) {
                throw new IllegalArgumentException("batch itemId must not be blank");
            }
            return QrCodeBatchItemResult.builder()
                    .itemId(item.getItemId())
                    .success(true)
                    .artifact(generate(item.getCommand()))
                    .build();
        };
    }

    /**
     * \u6784\u9020\u65e0\u6cd5\u5b8c\u6210\u7684\u6279\u91cf\u9879\u76ee\u7684\u8bca\u65ad\u7ed3\u679c\u3002
     * @param item \u6267\u884c\u5931\u8d25\u7684\u9879\u76ee\uff0c\u7f3a\u5c11\u9879\u76ee\u6570\u636e\u65f6\u53ef\u4ee5\u4e3a\u7a7a
     * @param errorCode \u5931\u8d25\u7c7b\u522b
     * @param errorMessage \u5931\u8d25\u8bf4\u660e
     * @return \u4fdd\u7559\u53ef\u7528\u9879\u76ee\u6807\u8bc6\u7684\u5931\u8d25\u7ed3\u679c
     */
    private QrCodeBatchItemResult failure(QrCodeBatchItem item, String errorCode, String errorMessage) {
        return QrCodeBatchItemResult.builder()
                .itemId(Objects.nonNull(item) ? item.getItemId() : null)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * \u4f7f\u7528 ZXing \u6309\u6307\u5b9a\u5c3a\u5bf8\u5c06\u6587\u672c\u7f16\u7801\u4e3a PNG \u4e8c\u7ef4\u7801\u3002
     * @param request \u5305\u542b\u7f16\u7801\u6587\u672c\u53ca\u50cf\u7d20\u5c3a\u5bf8\u7684\u8bf7\u6c42
     * @return \u751f\u6210\u7684 PNG \u56fe\u50cf\u5b57\u8282
     * @throws IllegalStateException \u7f16\u7801\u4e8c\u7ef4\u7801\u6216\u5199\u51fa\u56fe\u50cf\u5931\u8d25
     */
    private QrCodeOutput encode(io.ddd4j.extension.qrcode.model.QrCodeRequest request) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BitMatrix matrix = new QRCodeWriter().encode(request.getContent(), BarcodeFormat.QR_CODE,
                    request.getWidth(), request.getHeight());
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return new QrCodeOutput(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("QR code encoding failed", exception);
        }
    }

    /**
     * \u5c06\u8f93\u5165\u5b57\u8282\u8bfb\u53d6\u4e3a\u56fe\u50cf\u540e\u8c03\u7528 ZXing \u8bc6\u522b\u4e8c\u7ef4\u7801\u3002
     * @param bytes \u5f85\u8bc6\u522b\u7684\u56fe\u50cf\u5b57\u8282
     * @return \u5305\u542b\u8bc6\u522b\u6587\u672c\u7684\u5355\u9879\u7ed3\u679c\u5217\u8868
     * @throws IllegalStateException \u56fe\u50cf\u65e0\u6cd5\u8bfb\u53d6\u6216\u4e8c\u7ef4\u7801\u8bc6\u522b\u5931\u8d25
     */
    private List<QrCodeDecodeResult> decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (Objects.isNull(image)) {
                throw new IllegalArgumentException("bytes do not contain an image");
            }
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            Result result = new MultiFormatReader().decode(bitmap);
            return Collections.singletonList(new QrCodeDecodeResult(result.getText()));
        } catch (Exception exception) {
            throw new IllegalStateException("QR code decoding failed", exception);
        }
    }

    /**
     * \u4e3a\u6279\u91cf\u4e8c\u7ef4\u7801\u5de5\u4f5c\u4efb\u52a1\u521b\u5efa\u53ef\u8bc6\u522b\u7684\u5b88\u62a4\u7ebf\u7a0b\u5de5\u5382\u3002
     * @return \u4ee5\u9012\u589e\u5e8f\u53f7\u4e3a\u7ebf\u7a0b\u547d\u540d\u7684\u7ebf\u7a0b\u5de5\u5382
     */
    private ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "ddd4j-qrcode-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
