package io.ddd4j.extension.qlexpress.model;

import com.alibaba.qlexpress4.QLOptions;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;

/**
 * QLExpress 单次执行选项。
 *
 * <p>默认开启编译缓存，并设置 3 秒超时和 10000 的数组长度上限。
 */
@Getter
@Builder(toBuilder = true)
public final class QLExpressExecutionOptions {

    public static final long DEFAULT_TIMEOUT_MILLIS = 3000L;
    public static final int DEFAULT_MAX_ARRAY_LENGTH = 10000;

    @Builder.Default
    private final boolean precise = false;
    @Builder.Default
    private final boolean polluteUserContext = false;
    @Builder.Default
    private final long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    @Builder.Default
    private final Map<String, Object> attachments = Map.of();
    @Builder.Default
    private final boolean cache = true;
    @Builder.Default
    private final boolean avoidNullPointer = false;
    @Builder.Default
    private final int maxArrayLength = DEFAULT_MAX_ARRAY_LENGTH;
    @Builder.Default
    private final boolean traceExpression = false;
    @Builder.Default
    private final boolean shortCircuitDisabled = false;

    public static QLExpressExecutionOptions defaults() {
        return QLExpressExecutionOptions.builder().build();
    }

    public QLOptions toNativeOptions() {
        Map<String, Object> safeAttachments = Objects.isNull(attachments) ? Map.of() : Map.copyOf(attachments);
        return QLOptions.builder()
                .precise(precise)
                .polluteUserContext(polluteUserContext)
                .timeoutMillis(timeoutMillis)
                .attachments(safeAttachments)
                .cache(cache)
                .avoidNullPointer(avoidNullPointer)
                .maxArrLength(maxArrayLength)
                .traceExpression(traceExpression)
                .shortCircuitDisable(shortCircuitDisabled)
                .build();
    }
}
