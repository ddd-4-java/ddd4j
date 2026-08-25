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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Objects;

/**
 * {@link BigDecimal} 与 Excel 字符串互转的金额转换器。
 *
 * <p>特别适合对账、财务场景：
 * <ul>
 *   <li>读取：从字符串构造 {@link BigDecimal}（自动 trim，去除千分位逗号）</li>
 *   <li>写入：按指定格式输出（默认保留 2 位小数、千分位）</li>
 * </ul>
 *
 * <pre>{@code
 * @ExcelProperty(value = "金额", converter = BigDecimalStringConverter.class)
 * private BigDecimal amount;
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class BigDecimalStringConverter implements Converter<BigDecimal> {

    public static final String DEFAULT_PATTERN = "#,##0.00";

    private final String pattern;
    private final ThreadLocal<DecimalFormat> formatHolder;

    /**
     * 默认构造（{@link #DEFAULT_PATTERN}，2 位小数 + 千分位）。
     */
    public BigDecimalStringConverter() {
        this(DEFAULT_PATTERN);
    }

    /**
     * 自定义格式构造。
     *
     * @param pattern {@link DecimalFormat} 格式串
     */
    public BigDecimalStringConverter(String pattern) {
        this.pattern = pattern;
        this.formatHolder = ThreadLocal.withInitial(() -> {
            DecimalFormat df = new DecimalFormat(pattern);
            df.setRoundingMode(RoundingMode.HALF_UP);
            df.setParseBigDecimal(true);
            return df;
        });
    }

    @Override
    public Class<?> supportJavaTypeKey() {
        return BigDecimal.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public BigDecimal convertToJavaData(ReadCellData<?> cellData,
                                        ExcelContentProperty contentProperty,
                                        GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(cellData)) {
            return null;
        }
        String raw = cellData.getStringValue();
        if (StrKit.isBlank(raw)) {
            return null;
        }
        try {
            // 先用 DecimalFormat 解析（处理千分位、小数位）
            Number parsed = formatHolder.get().parse(raw.trim());
            if (parsed instanceof BigDecimal bd) {
                return bd;
            }
            return new BigDecimal(parsed.toString());
        } catch (ParseException pe) {
            // 退化为直接构造（兼容非标准格式）
            try {
                return new BigDecimal(raw.trim().replace(",", ""));
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Cannot parse '" + raw + "' as BigDecimal", nfe);
            }
        }
    }

    @Override
    public WriteCellData<?> convertToExcelData(BigDecimal value,
                                               ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(value)) {
            return new WriteCellData<>("");
        }
        return new WriteCellData<>(formatHolder.get().format(value));
    }

    /**
     * 返回当前格式串。
     *
     * @return 格式串
     */
    public String pattern() {
        return pattern;
    }
}
