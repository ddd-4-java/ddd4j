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

    /** \u9ed8\u8ba4\u91d1\u989d\u683c\u5f0f\uff1a\u4e24\u4f4d\u5c0f\u6570\u548c\u5343\u5206\u4f4d\u3002 */
    public static final String DEFAULT_PATTERN = "#,##0.00";

    /** \u91d1\u989d\u5bfc\u5165\u53ca\u5bfc\u51fa\u65f6\u4f7f\u7528\u7684\u5341\u8fdb\u5236\u683c\u5f0f\u3002 */
    private final String pattern;
    /** \u6bcf\u4e2a\u7ebf\u7a0b\u72ec\u7acb\u4f7f\u7528\u7684\u91d1\u989d\u89e3\u6790\u548c\u683c\u5f0f\u5316\u5668\u3002 */
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
     * \u58f0\u660e\u8f6c\u6362\u5668\u5904\u7406\u7684 Java \u7c7b\u578b\u3002
     * @return BigDecimal \u7684\u7c7b\u578b\u6807\u8bb0
     */
    @Override
    public Class<?> supportJavaTypeKey() {
        return BigDecimal.class;
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
     * \u4f7f\u7528\u5341\u8fdb\u5236\u683c\u5f0f\u89e3\u6790\u91d1\u989d\u3002
     * @param cellData \u5f53\u524d\u8bfb\u53d6\u7684\u6587\u672c\u5355\u5143\u683c
     * @param contentProperty \u5f53\u524d\u5b57\u6bb5\u7684 Excel \u5217\u5143\u6570\u636e
     * @param globalConfiguration \u5de5\u4f5c\u7c3f\u7ea7\u8f6c\u6362\u914d\u7f6e
     * @return \u89e3\u6790\u540e\u7684\u91d1\u989d\uff0c\u7a7a\u767d\u5355\u5143\u683c\u8fd4\u56de\u7a7a
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
     * \u6309\u5341\u8fdb\u5236\u683c\u5f0f\u5199\u51fa\u91d1\u989d\u3002
     * @param value \u5f85\u5bfc\u51fa\u7684\u503c
     * @param contentProperty \u5f53\u524d\u5b57\u6bb5\u7684 Excel \u5217\u5143\u6570\u636e
     * @param globalConfiguration \u5de5\u4f5c\u7c3f\u7ea7\u8f6c\u6362\u914d\u7f6e
     * @return \u5b57\u7b26\u4e32\u578b\u5199\u5165\u5355\u5143\u683c\uff0c\u7a7a\u5f15\u7528\u5199\u4e3a\u7a7a\u6587\u672c
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
