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

    /**
     * 默认允许的最大批量数量。
     */
    public static final int DEFAULT_MAX_BATCH_SIZE = 100;

    /**
     * 调度批量二维码生成任务的执行器。
     */
    private final ExecutorService executorService;
    /**
     * 单个批量生成请求允许包含的最大项目数。
     */
    private final int maxBatchSize;

    /**
     * 使用默认并发度和批量上限创建二维码服务。
     */
    public DefaultQrCodeService() {
        this(Math.min(Runtime.getRuntime().availableProcessors(), 8), DEFAULT_MAX_BATCH_SIZE);
    }

    /**
     * 使用指定并发度和批量上限创建二维码服务。
     *
     * @param concurrency  工作线程数量
     * @param maxBatchSize 最大批量数量
     */
    public DefaultQrCodeService(int concurrency, int maxBatchSize) {
        if (concurrency <= 0 || maxBatchSize <= 0) {
            throw new IllegalArgumentException("concurrency and maxBatchSize must be positive");
        }
        this.executorService = Executors.newFixedThreadPool(concurrency, daemonThreadFactory());
        this.maxBatchSize = maxBatchSize;
    }

    /**
     * 生成调用所需的标识或结果。
     *
     * <p>此实现调用 <code>command.getRequest()</code>。</p>
     *
     * @param command 生成命令
     * @return 二维码产物
     */
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

    /**
     * 将传入内容解码为可读取的数据。
     *
     * <p>此实现调用 <code>command.getRequest()</code>。</p>
     *
     * @param command 解码命令
     * @return 识别结果
     */
    @Override
    public QrCodeScanResult decode(DecodeQrCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.getRequest(), "command.request must not be null");
        return new QrCodeScanResult(command.getCorrelationId(), decode(command.getRequest().getBytes()));
    }

    /**
     * 根据批量请求生成二维码结果。
     *
     * <p>此实现调用 <code>items.size()</code>。</p>
     *
     * @param items 批量项目
     * @return 批量结果
     */
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
     * 构造批量项目的执行任务，在运行时校验项目标识并生成二维码。
     *
     * @param item 待生成二维码的批量项目
     * @return 产生该项目成功结果的可调度任务
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
     * 构造无法完成的批量项目的诊断结果。
     *
     * @param item         执行失败的项目，缺少项目数据时可以为空
     * @param errorCode    失败类别
     * @param errorMessage 失败说明
     * @return 保留可用项目标识的失败结果
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
     * 使用 ZXing 按指定尺寸将文本编码为 PNG 二维码。
     *
     * @param request 包含编码文本及像素尺寸的请求
     * @return 生成的 PNG 图像字节
     * @throws IllegalStateException 编码二维码或写出图像失败
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
     * 将输入字节读取为图像后调用 ZXing 识别二维码。
     *
     * @param bytes 待识别的图像字节
     * @return 包含识别文本的单项结果列表
     * @throws IllegalStateException 图像无法读取或二维码识别失败
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
     * 为批量二维码工作任务创建可识别的守护线程工厂。
     *
     * @return 以递增序号为线程命名的线程工厂
     */
    private ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "ddd4j-qrcode-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>此实现调用 <code>executorService.shutdownNow()</code>。</p>
     */
    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
