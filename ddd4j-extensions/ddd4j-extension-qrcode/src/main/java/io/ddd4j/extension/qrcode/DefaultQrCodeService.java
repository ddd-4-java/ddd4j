package io.ddd4j.extension.qrcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;

import io.ddd4j.extension.qrcode.batch.QrCodeBatchItem;
import io.ddd4j.extension.qrcode.batch.QrCodeBatchItemResult;
import io.ddd4j.extension.qrcode.batch.QrCodeBatchResult;
import io.ddd4j.extension.qrcode.command.DecodeQrCodeCommand;
import io.ddd4j.extension.qrcode.command.GenerateQrCodeCommand;
import io.ddd4j.extension.qrcode.result.QrCodeArtifact;
import io.ddd4j.extension.qrcode.result.QrCodeScanResult;
import com.google.zxing.QrCodeDecoder;
import com.google.zxing.QrCodeEncoder;
import com.google.zxing.QrCodes;
import com.google.zxing.exception.QrCodeErrorCode;
import com.google.zxing.exception.QrCodeException;

/** Default QR code service with bounded, order-preserving batch execution. */
public class DefaultQrCodeService implements QrCodeService, AutoCloseable {

    public static final int DEFAULT_MAX_BATCH_SIZE = 100;

    private final QrCodeEncoder encoder;
    private final QrCodeDecoder decoder;
    private final ExecutorService executorService;
    private final int maxBatchSize;

    public DefaultQrCodeService() {
        this(QrCodes.encoder(), QrCodes.decoder(), Math.min(Runtime.getRuntime().availableProcessors(), 8),
                DEFAULT_MAX_BATCH_SIZE);
    }

    public DefaultQrCodeService(QrCodeEncoder encoder, QrCodeDecoder decoder, int concurrency, int maxBatchSize) {
        this.encoder = Objects.requireNonNull(encoder, "encoder must not be null");
        this.decoder = Objects.requireNonNull(decoder, "decoder must not be null");
        if (concurrency <= 0 || maxBatchSize <= 0) {
            throw new IllegalArgumentException("concurrency and maxBatchSize must be positive");
        }
        this.executorService = Executors.newFixedThreadPool(concurrency, daemonThreadFactory());
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public QrCodeArtifact generate(GenerateQrCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.getRequest(), "command.request must not be null");
        return QrCodeArtifact.builder()
                .correlationId(command.getCorrelationId())
                .templateId(command.getTemplateId())
                .output(encoder.encode(command.getRequest()))
                .build();
    }

    @Override
    public QrCodeScanResult decode(DecodeQrCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.getRequest(), "command.request must not be null");
        return new QrCodeScanResult(command.getCorrelationId(), decoder.decode(command.getRequest()));
    }

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
                results.add(failure(items.get(index), QrCodeErrorCode.QRCODE_RENDER_FAILED.name(),
                        "Batch execution was interrupted"));
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                String errorCode = cause instanceof QrCodeException
                        ? ((QrCodeException) cause).getErrorCode().name()
                        : QrCodeErrorCode.QRCODE_RENDER_FAILED.name();
                results.add(failure(items.get(index), errorCode, cause.getMessage()));
            }
        }
        return new QrCodeBatchResult(results);
    }

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

    private QrCodeBatchItemResult failure(QrCodeBatchItem item, String errorCode, String errorMessage) {
        return QrCodeBatchItemResult.builder()
                .itemId(Objects.nonNull(item) ? item.getItemId() : null)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    private ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "ddd4j-qrcode-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
