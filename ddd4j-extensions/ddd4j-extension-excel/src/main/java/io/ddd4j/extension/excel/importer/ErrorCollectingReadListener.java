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
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 错误收集型监听器（不抛异常）。
 *
 * <p>实现 easyexcel 的 {@link AnalysisEventListener}，捕获每行的解析异常（含
 * {@code ExcelDataConvertException}），转为 {@link ImportError} 存入列表，
 * <b>不向 EasyExcel 抛出</b>，从而让解析继续走到下一行。
 *
 * <p>典型用途：业务侧希望拿到"全部行 + 错误明细"，而不是单点失败就中止。
 *
 * <h2>注意：监听器不能被 Spring 单例管理</h2>
 * <p>由于内部持有 {@code successData} / {@code errors} 状态，多线程或多次复用会污染数据。
 * 正确做法是<b>每次读取 new 一个</b>。如需注入 Spring Bean，通过构造参数传入。
 *
 * <pre>{@code
 * ErrorCollectingReadListener<UserVO> listener = new ErrorCollectingReadListener<>(
 *     user -> userService.validate(user)   // 可选：每行业务校验
 * );
 * ImportResult<UserVO> result = ExcelKit.importExcel(in, UserVO.class, listener);
 * if (result.hasErrors()) {
 *     log.warn("失败 {} 行", result.getErrors().size());
 * }
 * }</pre>
 *
 * @param <T> 数据类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ErrorCollectingReadListener<T> extends AnalysisEventListener<T> {

    /**
     * 已成功解析并通过行校验的数据。
     */
    @Getter
    private final List<T> successData = new ArrayList<>();

    /**
     * 导入时发生的逐行读取或校验错误。
     */
    @Getter
    private final List<ImportError> errors = new ArrayList<>();

    /**
     * 可选的每行业务校验函数；抛异常则记入 errors。
     */
    private final Consumer<T> rowValidator;

    /**
     * 默认构造（不做业务校验）。
     */
    public ErrorCollectingReadListener() {
        this(null);
    }

    /**
     * 带业务校验的构造。
     *
     * @param rowValidator 每行校验函数，可抛异常；为 null 表示不校验
     */
    public ErrorCollectingReadListener(Consumer<T> rowValidator) {
        this.rowValidator = rowValidator;
    }

    /**
     * 校验当前行，把数据或校验错误保存到对应的导入结果列表。
     *
     * @param data    当前解析的行
     * @param context 提供工作表及行号的读取上下文
     */
    @Override
    public void invoke(T data, AnalysisContext context) {
        if (Objects.nonNull(rowValidator)) {
            try {
                rowValidator.accept(data);
            } catch (Exception e) {
                errors.add(ImportError.ofValidation(context.readRowHolder().getRowIndex(), e.getMessage()));
                return;
            }
        }
        successData.add(data);
    }

    /**
     * 完成读取，已收集的数据和错误可通过结果方法获取。
     *
     * @param context 提供工作表及行号的读取上下文
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 无需后续动作；调用方通过 toResult() 取数
    }

    /**
     * 按当前行号收集转换异常，保留其他行的读取机会。
     *
     * @param exception 当前单元格或整行的转换异常
     * @param context   提供工作表及行号的读取上下文
     */
    @Override
    public void onException(Exception exception, AnalysisContext context) {
        // 关键：不抛出，转为 ImportError 收集
        long rowNo = context.readRowHolder().getRowIndex();
        errors.add(ImportError.of(rowNo, exception));
    }

    /**
     * 转换为不可变 {@link ImportResult}。
     *
     * @return 导入结果
     */
    public ImportResult<T> toResult() {
        return ImportResult.of(successData, errors);
    }
}
