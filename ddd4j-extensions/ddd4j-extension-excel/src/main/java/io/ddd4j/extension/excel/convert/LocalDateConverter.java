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
 * <pre>{@code
 * // 字段级
 * @ExcelProperty(value = "日期", converter = LocalDateConverter.class)
 * private LocalDate createTime;
 *
 * // 全局
 * EasyExcel.read(in, OrderVO.class, listener)
 *     .registerConverter(new LocalDateConverter())
 *     .sheet().doRead();
 * }</pre>
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

    @Override
    public Class<?> supportJavaTypeKey() {
        return LocalDate.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public LocalDate convertToJavaData(ReadCellData<?> cellData,
                                       ExcelContentProperty contentProperty,
                                       GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(cellData) || StrKit.isEmpty(cellData.getStringValue())) {
            return null;
        }
        return LocalDate.parse(cellData.getStringValue(), formatter);
    }

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
