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

    /** \u8fbe\u5230\u8be5\u8bb0\u5f55\u6570\u65f6\u7acb\u5373\u63d0\u4ea4\u4e00\u6279\u6570\u636e\u3002 */
    private final int batchSize;
    /** \u63a5\u6536\u5df2\u89e3\u6790\u8bb0\u5f55\u6279\u6b21\u7684\u6d88\u8d39\u56de\u8c03\u3002 */
    private final Consumer<List<T>> batchConsumer;
    /** \u6682\u5b58\u5c1a\u672a\u63d0\u4ea4\u7ed9\u6279\u6b21\u56de\u8c03\u7684\u8bb0\u5f55\u3002 */
    private final List<T> cached;

    /**
     * 构造批量监听器。
     *
     * @param batchSize     批大小（建议 500 ~ 5000）
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

    /**
     * \u7f13\u5b58\u5f53\u524d\u884c\u5e76\u5728\u8fbe\u5230\u6279\u6b21\u5927\u5c0f\u65f6\u4ea4\u7ed9\u6d88\u8d39\u56de\u8c03\u3002
     * @param data \u5f53\u524d\u89e3\u6790\u7684\u884c
     * @param context \u63d0\u4f9b\u5de5\u4f5c\u8868\u53ca\u884c\u53f7\u7684\u8bfb\u53d6\u4e0a\u4e0b\u6587
     */
    @Override
    public void invoke(T data, AnalysisContext context) {
        cached.add(data);
        if (cached.size() >= batchSize) {
            flush();
        }
    }

    /**
     * \u63d0\u4ea4\u8bfb\u53d6\u7ed3\u675f\u65f6\u5c1a\u672a\u6d88\u8d39\u7684\u5c3e\u6279\u8bb0\u5f55\u3002
     * @param context \u63d0\u4f9b\u5de5\u4f5c\u8868\u53ca\u884c\u53f7\u7684\u8bfb\u53d6\u4e0a\u4e0b\u6587
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
        log.debug("BatchReadListener finished, sheet={}", context.readSheetHolder().getSheetName());
    }

    /**
     * \u8bb0\u5f55\u574f\u884c\u7684\u7d22\u5f15\u548c\u5f02\u5e38\u4fe1\u606f\uff0c\u7ee7\u7eed\u8bfb\u53d6\u540e\u7eed\u884c\u3002
     * @param exception \u5f53\u524d\u884c\u89e3\u6790\u5f02\u5e38
     * @param context \u63d0\u4f9b\u5de5\u4f5c\u8868\u53ca\u884c\u53f7\u7684\u8bfb\u53d6\u4e0a\u4e0b\u6587
     */
    @Override
    public void onException(Exception exception, AnalysisContext context) {
        // 单行失败不影响整批；记录日志后继续。如需精细错误收集，请使用 ErrorCollectingReadListener
        log.warn("Row {} parse failed, skipped: {}", context.readRowHolder().getRowIndex(), exception.getMessage());
    }

    /** \u628a\u6682\u5b58\u8bb0\u5f55\u7684\u526f\u672c\u4ea4\u7ed9\u6279\u6b21\u56de\u8c03\uff0c\u5e76\u5728\u56de\u8c03\u7ed3\u675f\u540e\u6e05\u7a7a\u6682\u5b58\u5217\u8868\u3002 */
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
