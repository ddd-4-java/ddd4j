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

    /**
     * 默认金额格式：两位小数和千分位。
     */
    public static final String DEFAULT_PATTERN = "#,##0.00";

    /**
     * 金额导入及导出时使用的十进制格式。
     */
    private final String pattern;
    /**
     * 每个线程独立使用的金额解析和格式化器。
     */
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

    /**
     * 声明转换器处理的 Java 类型。
     *
     * @return BigDecimal 的类型标记
     */
    @Override
    public Class<?> supportJavaTypeKey() {
        return BigDecimal.class;
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
     * 使用十进制格式解析金额。
     *
     * @param cellData            当前读取的文本单元格
     * @param contentProperty     当前字段的 Excel 列元数据
     * @param globalConfiguration 工作簿级转换配置
     * @return 解析后的金额，空白单元格返回空
     */
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

    /**
     * 按十进制格式写出金额。
     *
     * @param value               待导出的值
     * @param contentProperty     当前字段的 Excel 列元数据
     * @param globalConfiguration 工作簿级转换配置
     * @return 字符串型写入单元格，空引用写为空文本
     */
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
