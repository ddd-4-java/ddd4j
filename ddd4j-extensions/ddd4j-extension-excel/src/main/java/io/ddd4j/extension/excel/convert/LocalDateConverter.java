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
package io.ddd4j.extension.excel.convert;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import io.ddd4j.kit.lang.StrKit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * {@link LocalDate} 与 Excel 字符串互转的默认转换器。
 *
 * <p>easyexcel 默认不支持 Java 8 时间 API（{@code LocalDate}/{@code LocalDateTime}）的自动转换，
 * 需要业务侧自行注册。本类作为通用转换器，默认使用 ISO 格式 {@code yyyy-MM-dd}。
 *
 * <h3>用法</h3>
 * <!-- 样例行首 @ 会被解析为 doc 块标签，必须转义为 &#64; -->
 * <pre>
 * // 字段级
 * &#64;ExcelProperty(value = "日期", converter = LocalDateConverter.class)
 * private LocalDate createTime;
 *
 * // 全局
 * EasyExcel.read(in, OrderVO.class, listener)
 *     .registerConverter(new LocalDateConverter())
 *     .sheet().doRead();
 * </pre>
 *
 * <p>如需自定义格式，使用 {@link #LocalDateConverter(String)} 构造。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class LocalDateConverter implements Converter<LocalDate> {

    /**
     * 默认 ISO 日期格式 {@code yyyy-MM-dd}。
     */
    public static final String DEFAULT_PATTERN = "yyyy-MM-dd";

    /** \u5355\u5143\u683c\u6587\u672c\u4e0e Java \u65f6\u95f4\u7c7b\u578b\u4e4b\u95f4\u8f6c\u6362\u4f7f\u7528\u7684\u683c\u5f0f\u5668\u3002 */
    private final DateTimeFormatter formatter;

    /**
     * 默认构造（使用 {@link #DEFAULT_PATTERN}）。
     */
    public LocalDateConverter() {
        this(DEFAULT_PATTERN);
    }

    /**
     * 自定义格式构造。
     *
     * @param pattern 日期格式（参照 {@link DateTimeFormatter#ofPattern(String)}）
     */
    public LocalDateConverter(String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    /**
     * \u58f0\u660e\u8f6c\u6362\u5668\u5904\u7406\u7684 Java \u7c7b\u578b\u3002
     * @return LocalDate \u7684\u7c7b\u578b\u6807\u8bb0
     */
    @Override
    public Class<?> supportJavaTypeKey() {
        return LocalDate.class;
    }

    /**
     * \u58f0\u660e\u8f6c\u6362\u5668\u5904\u7406\u7684 Excel \u5355\u5143\u683c\u7c7b\u578b\u3002
     * @return \u5b57\u7b26\u4e32\u578b\u5355\u5143\u683c\u7c7b\u578b
     */
    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    /**
     * \u4f7f\u7528\u914d\u7f6e\u7684\u683c\u5f0f\u89e3\u6790\u65e5\u671f\u6587\u672c\u3002
     * @param cellData \u5f53\u524d\u8bfb\u53d6\u7684\u6587\u672c\u5355\u5143\u683c
     * @param contentProperty \u5f53\u524d\u5b57\u6bb5\u7684 Excel \u5217\u5143\u6570\u636e
     * @param globalConfiguration \u5de5\u4f5c\u7c3f\u7ea7\u8f6c\u6362\u914d\u7f6e
     * @return \u89e3\u6790\u540e\u7684\u65e5\u671f\uff0c\u7a7a\u5355\u5143\u683c\u8fd4\u56de\u7a7a
     */
    @Override
    public LocalDate convertToJavaData(ReadCellData<?> cellData,
                                       ExcelContentProperty contentProperty,
                                       GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(cellData) || StrKit.isEmpty(cellData.getStringValue())) {
            return null;
        }
        return LocalDate.parse(cellData.getStringValue(), formatter);
    }

    /**
     * \u4f7f\u7528\u914d\u7f6e\u7684\u683c\u5f0f\u5199\u51fa\u65e5\u671f\u6587\u672c\u3002
     * @param value \u5f85\u5bfc\u51fa\u7684\u503c
     * @param contentProperty \u5f53\u524d\u5b57\u6bb5\u7684 Excel \u5217\u5143\u6570\u636e
     * @param globalConfiguration \u5de5\u4f5c\u7c3f\u7ea7\u8f6c\u6362\u914d\u7f6e
     * @return \u5b57\u7b26\u4e32\u578b\u5199\u5165\u5355\u5143\u683c\uff0c\u7a7a\u5f15\u7528\u5199\u4e3a\u7a7a\u6587\u672c
     */
    @Override
    public WriteCellData<?> convertToExcelData(LocalDate value,
                                               ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(value)) {
            return new WriteCellData<>("");
        }
        return new WriteCellData<>(value.format(formatter));
    }
}
