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
 * <pre>{@code
 * public class OrderStatusConverter extends EnumNameConverter<OrderStatus> {
 *     public OrderStatusConverter() { super(OrderStatus.class); }
 * }
 *
 * @ExcelProperty(value = "状态", converter = OrderStatusConverter.class)
 * private OrderStatus status;
 * }</pre>
 *
 * <p>如需基于 label/i18n 的枚举转换，可继承后重写
 * {@link #convertToExcelDataLabel(Enum)} 与 {@link #convertFromLabel(String)}。
 *
 * @param <E> 枚举类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class EnumNameConverter<E extends Enum<E>> implements Converter<E> {

    private final Class<E> enumType;
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

    @Override
    public Class<?> supportJavaTypeKey() {
        return enumType;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public E convertToJavaData(ReadCellData<?> cellData,
                               ExcelContentProperty contentProperty,
                               GlobalConfiguration globalConfiguration) {
        if (Objects.isNull(cellData) || StrKit.isEmpty(cellData.getStringValue())) {
            return null;
        }
        return convertFromLabel(cellData.getStringValue());
    }

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
