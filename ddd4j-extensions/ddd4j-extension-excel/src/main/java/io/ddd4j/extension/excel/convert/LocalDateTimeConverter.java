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

    public static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final DateTimeFormatter formatter;

    public LocalDateTimeConverter() {
        this(DEFAULT_PATTERN);
    }

    public LocalDateTimeConverter(String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    @Override
    public Class<?> supportJavaTypeKey() {
        return LocalDateTime.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public LocalDateTime convertToJavaData(ReadCellData<?> cellData,
                                           ExcelContentProperty contentProperty,
                                           GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(cellData) || StrKit.isEmpty(cellData.getStringValue())) {
            return null;
        }
        return LocalDateTime.parse(cellData.getStringValue(), formatter);
    }

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
