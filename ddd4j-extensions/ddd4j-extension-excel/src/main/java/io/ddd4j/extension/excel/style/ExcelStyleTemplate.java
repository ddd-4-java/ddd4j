package io.ddd4j.extension.excel.style;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;

/**
 * Excel 预设样式模板。
 *
 * <p>每种模板封装一组 {@link StylePreset}（表头底色、内容对齐、是否斑马线等），
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

    private final short headerColor;
    private final HorizontalAlignment contentAlign;
    private final boolean zebraRow;

    ExcelStyleTemplate(short headerColor, HorizontalAlignment contentAlign, boolean zebraRow) {
        this.headerColor = headerColor;
        this.contentAlign = contentAlign;
        this.zebraRow = zebraRow;
    }

    /**
     * 表头底色（POI IndexedColors 索引）。
     */
    public short headerColor() {
        return headerColor;
    }

    /**
     * 内容对齐方式。
     */
    public HorizontalAlignment contentAlign() {
        return contentAlign;
    }

    /**
     * 是否启用斑马线（偶数行底色）。
     */
    public boolean zebraRow() {
        return zebraRow;
    }
}
