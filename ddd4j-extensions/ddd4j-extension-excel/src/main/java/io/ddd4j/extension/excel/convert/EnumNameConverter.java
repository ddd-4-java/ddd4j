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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用枚举转换器（基于枚举常量名 {@code name()}）。
 *
 * <p>在 DTO 中泛型字段无法直接标注 {@code converter}，可继承本类指定具体类型：
 *
 * <!-- 样例行首 @ 会被解析为 doc 块标签，必须转义为 &#64; -->
 * <pre>
 * public class OrderStatusConverter extends EnumNameConverter&lt;OrderStatus&gt; {
 *     public OrderStatusConverter() { super(OrderStatus.class); }
 * }
 *
 * &#64;ExcelProperty(value = "状态", converter = OrderStatusConverter.class)
 * private OrderStatus status;
 * </pre>
 *
 * <p>如需基于 label/i18n 的枚举转换，可继承后重写
 * {@link #convertToExcelDataLabel(Enum)} 与 {@link #convertFromLabel(String)}。
 *
 * @param <E> 枚举类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class EnumNameConverter<E extends Enum<E>> implements Converter<E> {

    /** \u5355\u5143\u683c\u6587\u672c\u8f6c\u6362\u7684\u76ee\u6807\u679a\u4e3e\u7c7b\u578b\u3002 */
    private final Class<E> enumType;
    /** \u6309\u5bfc\u5165\u6587\u672c\u7d22\u5f15\u76ee\u6807\u679a\u4e3e\u5e38\u91cf\u7684\u6620\u5c04\u3002 */
    private final Map<String, E> nameIndex;

    /**
     * 构造枚举转换器。
     *
     * @param enumType 枚举类型（不可为 null）
     */
    protected EnumNameConverter(Class<E> enumType) {
        if (Objects.isNull(enumType)) {
            throw new IllegalArgumentException("enumType must not be null");
        }
        this.enumType = enumType;
        this.nameIndex = Arrays.stream(enumType.getEnumConstants())
                .collect(Collectors.toMap(Enum::name, Function.identity(), (a, b) -> a));
    }

    /**
     * \u8bfb\u53d6<code>enumType</code> \u5b57\u6bb5\u3002
     * @return <code>enumType</code> \u5b57\u6bb5
     */
    @Override
    public Class<?> supportJavaTypeKey() {
        return enumType;
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
     * \u6309\u6807\u7b7e\u67e5\u627e\u679a\u4e3e\u5e38\u91cf\u3002
     * @param cellData \u5f53\u524d\u8bfb\u53d6\u7684\u6587\u672c\u5355\u5143\u683c
     * @param contentProperty \u5f53\u524d\u5b57\u6bb5\u7684 Excel \u5217\u5143\u6570\u636e
     * @param globalConfiguration \u5de5\u4f5c\u7c3f\u7ea7\u8f6c\u6362\u914d\u7f6e
     * @return \u5bf9\u5e94\u7684\u679a\u4e3e\u5e38\u91cf\uff0c\u7a7a\u5355\u5143\u683c\u8fd4\u56de\u7a7a
     */
    @Override
    public E convertToJavaData(ReadCellData<?> cellData,
                               ExcelContentProperty contentProperty,
                               GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(cellData) || StrKit.isEmpty(cellData.getStringValue())) {
            return null;
        }
        return convertFromLabel(cellData.getStringValue());
    }

    /**
     * \u5c06\u679a\u4e3e\u5e38\u91cf\u683c\u5f0f\u5316\u4e3a\u5bfc\u51fa\u6807\u7b7e\u3002
     * @param value \u5f85\u5bfc\u51fa\u7684\u503c
     * @param contentProperty \u5f53\u524d\u5b57\u6bb5\u7684 Excel \u5217\u5143\u6570\u636e
     * @param globalConfiguration \u5de5\u4f5c\u7c3f\u7ea7\u8f6c\u6362\u914d\u7f6e
     * @return \u5b57\u7b26\u4e32\u578b\u5199\u5165\u5355\u5143\u683c\uff0c\u7a7a\u5f15\u7528\u5199\u4e3a\u7a7a\u6587\u672c
     */
    @Override
    public WriteCellData<?> convertToExcelData(E value,
                                               ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(value)) {
            return new WriteCellData<>("");
        }
        return new WriteCellData<>(convertToExcelDataLabel(value));
    }

    /**
     * 默认按枚举 {@code name()} 输出。子类可重写为 label。
     *
     * @param value 枚举值
     * @return Excel 单元格字符串
     */
    protected String convertToExcelDataLabel(E value) {
        return value.name();
    }

    /**
     * 默认按枚举 {@code name()} 解析。子类可重写为按 label 反查。
     *
     * @param label 单元格字符串
     * @return 枚举值；找不到抛 {@link IllegalArgumentException}
     */
    protected E convertFromLabel(String label) {
        E v = nameIndex.get(label);
        if (Objects.isNull(v)) {
            throw new IllegalArgumentException(
                    "Unknown enum value '" + label + "' for " + enumType.getName());
        }
        return v;
    }
}
