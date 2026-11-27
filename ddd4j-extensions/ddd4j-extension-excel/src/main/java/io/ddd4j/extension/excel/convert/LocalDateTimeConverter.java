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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * {@link LocalDateTime} 与 Excel 字符串互转的默认转换器。
 *
 * <p>默认格式 {@code yyyy-MM-dd HH:mm:ss}。
 *
 * <pre>{@code
 * @ExcelProperty(value = "创建时间", converter = LocalDateTimeConverter.class)
 * private LocalDateTime createTime;
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class LocalDateTimeConverter implements Converter<LocalDateTime> {

    /**
     * 默认日期时间格式。
     */
    public static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 单元格文本与 Java 时间类型之间转换使用的格式器。
     */
    private final DateTimeFormatter formatter;

    /**
     * 使用默认日期时间格式创建转换器。
     */
    public LocalDateTimeConverter() {
        this(DEFAULT_PATTERN);
    }

    /**
     * 使用指定日期时间格式创建转换器。
     *
     * @param pattern 日期时间格式
     */
    public LocalDateTimeConverter(String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    /**
     * 声明转换器处理的 Java 类型。
     *
     * @return LocalDateTime 的类型标记
     */
    @Override
    public Class<?> supportJavaTypeKey() {
        return LocalDateTime.class;
    }

    /**
     * 声明转换器处理的 Excel 单元格类型。
     *
     * @return 字符串型单元格类型
     */
    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    /**
     * 使用配置的格式解析日期时间文本。
     *
     * @param cellData            当前读取的文本单元格
     * @param contentProperty     当前字段的 Excel 列元数据
     * @param globalConfiguration 工作簿级转换配置
     * @return 解析后的日期时间，空单元格返回空
     */
    @Override
    public LocalDateTime convertToJavaData(ReadCellData<?> cellData,
                                           ExcelContentProperty contentProperty,
                                           GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(cellData) || StrKit.isEmpty(cellData.getStringValue())) {
            return null;
        }
        return LocalDateTime.parse(cellData.getStringValue(), formatter);
    }

    /**
     * 使用配置的格式写出日期时间文本。
     *
     * @param value               待导出的值
     * @param contentProperty     当前字段的 Excel 列元数据
     * @param globalConfiguration 工作簿级转换配置
     * @return 字符串型写入单元格，空引用写为空文本
     */
    @Override
    public WriteCellData<?> convertToExcelData(LocalDateTime value,
                                               ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(value)) {
            return new WriteCellData<>("");
        }
        return new WriteCellData<>(value.format(formatter));
    }
}
