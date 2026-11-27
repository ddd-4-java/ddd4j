package io.ddd4j.extension.excel.style;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;

/**
 * Excel 预设样式模板。
 *
 * <p>每个枚举值封装一组样式预设（{@link #headerColor()} 表头底色、
 * {@link #contentAlign()} 内容对齐、{@link #zebraRow()} 是否斑马线），
 * 由 {@link DefaultCellStyleStrategy} 读取后翻译为 easyexcel 样式对象。
 *
 * <p>新增模板只需追加枚举值并填写预设即可。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum ExcelStyleTemplate {

    /**
     * 默认：浅灰表头、居中、细边框。
     */
    DEFAULT(IndexedColors.GREY_25_PERCENT.getIndex(), HorizontalAlignment.CENTER, false),

    /**
     * 列表型：蓝色表头、左对齐、无斑马线。
     */
    LIST(IndexedColors.PALE_BLUE.getIndex(), HorizontalAlignment.LEFT, false),

    /**
     * 财务型：深蓝表头、右对齐（适合数字列）、无斑马线。
     */
    FINANCE(IndexedColors.DARK_BLUE.getIndex(), HorizontalAlignment.RIGHT, false),

    /**
     * 简洁型：白色表头、居中、无边框。
     */
    MINIMAL(IndexedColors.WHITE.getIndex(), HorizontalAlignment.CENTER, false),

    /**
     * 斑马线：浅蓝表头、居中、偶数行浅灰背景。
     */
    ZEBRA(IndexedColors.ROYAL_BLUE.getIndex(), HorizontalAlignment.CENTER, true);

    /** \u8868\u5934\u4f7f\u7528\u7684\u8c03\u8272\u677f\u989c\u8272\u7d22\u5f15\u3002 */
    private final short headerColor;
    /** \u5185\u5bb9\u5355\u5143\u683c\u7684\u6c34\u5e73\u5bf9\u9f50\u65b9\u5f0f\u3002 */
    private final HorizontalAlignment contentAlign;
    /** \u662f\u5426\u4e3a\u5185\u5bb9\u533a\u57df\u542f\u7528\u4ea4\u66ff\u5e95\u8272\u3002 */
    private final boolean zebraRow;

    /**
     * \u4fdd\u5b58\u8868\u5934\u586b\u5145\u8272\u3001\u6b63\u6587\u5bf9\u9f50\u548c\u4ea4\u66ff\u5e95\u8272\u5f00\u5173\u3002
     * @param headerColor \u8868\u5934\u989c\u8272\u7d22\u5f15
     * @param contentAlign \u5185\u5bb9\u7684\u6c34\u5e73\u5bf9\u9f50\u65b9\u5f0f
     * @param zebraRow \u662f\u5426\u4e3a\u6b63\u6587\u542f\u7528\u4ea4\u66ff\u5e95\u8272
     */
    ExcelStyleTemplate(short headerColor, HorizontalAlignment contentAlign, boolean zebraRow) {
        this.headerColor = headerColor;
        this.contentAlign = contentAlign;
        this.zebraRow = zebraRow;
    }

    /**
     * \u8868\u5934\u5e95\u8272\uff08POI IndexedColors \u7d22\u5f15\uff09\u3002
     *
     * @return \u8868\u5934\u5e95\u8272\u7d22\u5f15
     */
    public short headerColor() {
        return headerColor;
    }

    /**
     * \u5185\u5bb9\u5bf9\u9f50\u65b9\u5f0f\u3002
     *
     * @return \u5185\u5bb9\u5bf9\u9f50\u65b9\u5f0f
     */
    public HorizontalAlignment contentAlign() {
        return contentAlign;
    }

    /**
     * \u662f\u5426\u542f\u7528\u6591\u9a6c\u7ebf\uff08\u5076\u6570\u884c\u5e95\u8272\uff09\u3002
     *
     * @return \u542f\u7528\u6591\u9a6c\u7ebf\u65f6\u4e3a true
     */
    public boolean zebraRow() {
        return zebraRow;
    }
}
