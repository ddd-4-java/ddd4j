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
 * <h3>注意：监听器不能被 Spring 单例管理</h3>
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

    /** \u5df2\u6210\u529f\u89e3\u6790\u5e76\u901a\u8fc7\u884c\u6821\u9a8c\u7684\u6570\u636e\u3002 */
    @Getter
    private final List<T> successData = new ArrayList<>();

    /** \u5bfc\u5165\u65f6\u53d1\u751f\u7684\u9010\u884c\u8bfb\u53d6\u6216\u6821\u9a8c\u9519\u8bef\u3002 */
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
     * \u6821\u9a8c\u5f53\u524d\u884c\uff0c\u628a\u6570\u636e\u6216\u6821\u9a8c\u9519\u8bef\u4fdd\u5b58\u5230\u5bf9\u5e94\u7684\u5bfc\u5165\u7ed3\u679c\u5217\u8868\u3002
     * @param data \u5f53\u524d\u89e3\u6790\u7684\u884c
     * @param context \u63d0\u4f9b\u5de5\u4f5c\u8868\u53ca\u884c\u53f7\u7684\u8bfb\u53d6\u4e0a\u4e0b\u6587
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
     * \u5b8c\u6210\u8bfb\u53d6\uff0c\u5df2\u6536\u96c6\u7684\u6570\u636e\u548c\u9519\u8bef\u53ef\u901a\u8fc7\u7ed3\u679c\u65b9\u6cd5\u83b7\u53d6\u3002
     * @param context \u63d0\u4f9b\u5de5\u4f5c\u8868\u53ca\u884c\u53f7\u7684\u8bfb\u53d6\u4e0a\u4e0b\u6587
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 无需后续动作；调用方通过 toResult() 取数
    }

    /**
     * \u6309\u5f53\u524d\u884c\u53f7\u6536\u96c6\u8f6c\u6362\u5f02\u5e38\uff0c\u4fdd\u7559\u5176\u4ed6\u884c\u7684\u8bfb\u53d6\u673a\u4f1a\u3002
     * @param exception \u5f53\u524d\u5355\u5143\u683c\u6216\u6574\u884c\u7684\u8f6c\u6362\u5f02\u5e38
     * @param context \u63d0\u4f9b\u5de5\u4f5c\u8868\u53ca\u884c\u53f7\u7684\u8bfb\u53d6\u4e0a\u4e0b\u6587
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
