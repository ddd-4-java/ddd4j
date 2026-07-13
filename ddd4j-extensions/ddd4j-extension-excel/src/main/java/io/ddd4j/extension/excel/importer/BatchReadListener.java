package io.ddd4j.extension.excel.importer;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 批量入库监听器（流式 + 自动分批）。
 *
 * <p>每读满 {@link #batchSize} 行就回调 {@link #batchConsumer}（典型用法：批量入库 DAO），
 * 全部读完时再 flush 剩余。相比一次性 {@code doReadSync()}，可显著降低内存占用，
 * 适合百万级数据导入。
 *
 * <p>官方推荐：mybatis 应使用 {@code batchInsert}，而非循环 {@code insert}。
 *
 * <h3>注意：监听器不能被 Spring 单例管理</h3>
 * <p>每次导入必须 new 一个，否则 {@code cached} 会跨任务污染。如需注入 Spring Bean，
 * 通过构造参数传入 batchConsumer 即可（lambda 中可引用 Spring Service）。
 *
 * <pre>{@code
 * BatchReadListener<UserVO> listener = new BatchReadListener<>(
 *     2000, users -> userMapper.batchInsert(users)
 * );
 * ExcelKit.importExcel(in, UserVO.class, listener);
 * }</pre>
 *
 * @param <T> 数据类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class BatchReadListener<T> implements ReadListener<T> {

    private final int batchSize;
    private final Consumer<List<T>> batchConsumer;
    private final List<T> cached;

    /**
     * 构造批量监听器。
     *
     * @param batchSize    批大小（建议 500 ~ 5000）
     * @param batchConsumer 批回调（达到批大小时触发）
     */
    public BatchReadListener(int batchSize, Consumer<List<T>> batchConsumer) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        this.batchSize = batchSize;
        this.batchConsumer = batchConsumer;
        this.cached = new ArrayList<>(batchSize);
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        cached.add(data);
        if (cached.size() >= batchSize) {
            flush();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
        log.debug("BatchReadListener finished, sheet={}",
                context.readSheetHolder().getSheetName());
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) {
        // 单行失败不影响整批；记录日志后继续。如需精细错误收集，请使用 ErrorCollectingReadListener
        log.warn("Row {} parse failed, skipped: {}",
                context.getCurrentRowNum(), exception.getMessage());
    }

    private void flush() {
        if (cached.isEmpty()) {
            return;
        }
        try {
            batchConsumer.accept(new ArrayList<>(cached));
        } finally {
            cached.clear();
        }
    }
}
