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

    /** \u9ed8\u8ba4\u6267\u884c\u8d85\u65f6\u65f6\u95f4\uff08\u6beb\u79d2\uff09\u3002 */
    public static final long DEFAULT_TIMEOUT_MILLIS = 3000L;
    /** \u9ed8\u8ba4\u6570\u7ec4\u957f\u5ea6\u4e0a\u9650\u3002 */
    public static final int DEFAULT_MAX_ARRAY_LENGTH = 10000;

    /** \u662f\u5426\u542f\u7528\u9ad8\u7cbe\u5ea6\u6570\u503c\u8fd0\u7b97\u3002 */
    @Builder.Default
    private final boolean precise = false;
    /** \u662f\u5426\u628a\u8868\u8fbe\u5f0f\u4ea7\u751f\u7684\u53d8\u91cf\u4fee\u6539\u5199\u56de\u7528\u6237\u4e0a\u4e0b\u6587\u3002 */
    @Builder.Default
    private final boolean polluteUserContext = false;
    /** \u5355\u6b21\u8868\u8fbe\u5f0f\u6267\u884c\u7684\u8d85\u65f6\u65f6\u95f4\uff0c\u5355\u4f4d\u4e3a\u6beb\u79d2\u3002 */
    @Builder.Default
    private final long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    /** \u968f\u5f53\u524d\u6267\u884c\u4e0b\u53d1\u5230\u5f15\u64ce\u4e0a\u4e0b\u6587\u7684\u989d\u5916\u5bf9\u8c61\u3002 */
    @Builder.Default
    private final Map<String, Object> attachments = Map.of();
    /** \u662f\u5426\u590d\u7528\u5df2\u89e3\u6790\u7684\u8868\u8fbe\u5f0f\u7ed3\u679c\u3002 */
    @Builder.Default
    private final boolean cache = true;
    /** \u662f\u5426\u542f\u7528\u8868\u8fbe\u5f0f\u6267\u884c\u5668\u7684\u7a7a\u5f15\u7528\u4fdd\u62a4\u3002 */
    @Builder.Default
    private final boolean avoidNullPointer = false;
    /** \u8868\u8fbe\u5f0f\u53ef\u521b\u5efa\u6216\u5904\u7406\u7684\u6570\u7ec4\u957f\u5ea6\u9650\u5236\u3002 */
    @Builder.Default
    private final int maxArrayLength = DEFAULT_MAX_ARRAY_LENGTH;
    /** \u5f53\u524d\u8868\u8fbe\u5f0f\u6267\u884c\u662f\u5426\u6536\u96c6\u8ddf\u8e2a\u4fe1\u606f\u3002 */
    @Builder.Default
    private final boolean traceExpression = false;
    /** \u662f\u5426\u5173\u95ed\u5e03\u5c14\u8fd0\u7b97\u7684\u77ed\u8def\u6c42\u503c\u3002 */
    @Builder.Default
    private final boolean shortCircuitDisabled = false;

    /**
     * \u521b\u5efa\u9ed8\u8ba4\u6267\u884c\u9009\u9879\u3002
     * @return \u9ed8\u8ba4\u6267\u884c\u9009\u9879
     */
    public static QLExpressExecutionOptions defaults() {
        return QLExpressExecutionOptions.builder().build();
    }

    /**
     * \u8f6c\u6362\u4e3a QLExpress \u539f\u751f\u6267\u884c\u9009\u9879\u3002
     * @return \u539f\u751f\u6267\u884c\u9009\u9879
     */
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
