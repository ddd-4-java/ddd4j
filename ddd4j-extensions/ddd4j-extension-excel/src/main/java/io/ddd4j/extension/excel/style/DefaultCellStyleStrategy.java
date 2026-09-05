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
package io.ddd4j.extension.excel.style;

import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

/**
 * 默认样式策略（修复原 {@code CellStyleStrategy} 的 bug）。
 *
 * <p><b>修复要点</b>：
 * <ul>
 *   <li>原实现 {@code columnIndexes.get(0)} 在传入空列表时 NPE/越界，本类直接基于
 *       {@link ExcelStyleTemplate} 派生样式，不再硬编码"首列特殊"逻辑</li>
 *   <li>样式对象在构造期一次创建，写入阶段共享，避免触发 easyexcel 提示的"6W 样式上限"</li>
 *   <li>"首列特殊"等业务需求请改用 {@link io.ddd4j.extension.excel.export.WriteOptions#addHandler}
 *       注入额外 {@code WriteHandler}</li>
 * </ul>
 *
 * <p>继承 {@link HorizontalCellStyleStrategy} 复用 easyexcel 的水平样式处理流程，
 * 仅在构造期注入预构建的样式对象。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DefaultCellStyleStrategy extends HorizontalCellStyleStrategy {

    /**
     * 用指定模板构造。
     *
     * @param template 样式模板
     */
    public DefaultCellStyleStrategy(ExcelStyleTemplate template) {
        super(buildHeadStyle(template), buildContentStyle(template));
    }

    /**
     * 用默认模板构造。
     */
    public DefaultCellStyleStrategy() {
        this(ExcelStyleTemplate.DEFAULT);
    }

    /**
     * 直接传入两个样式对象（高级用法）。
     *
     * @param headCellStyle    表头样式
     * @param contentCellStyle 内容样式
     */
    public DefaultCellStyleStrategy(WriteCellStyle headCellStyle, WriteCellStyle contentCellStyle) {
        super(headCellStyle, contentCellStyle);
    }

    private static WriteCellStyle buildHeadStyle(ExcelStyleTemplate t) {
        WriteCellStyle style = new WriteCellStyle();
        style.setFillForegroundColor(t.headerColor());
        style.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        style.setHorizontalAlignment(HorizontalAlignment.CENTER);
        applyBorder(style);

        WriteFont font = new WriteFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        style.setWriteFont(font);
        return style;
    }

    private static WriteCellStyle buildContentStyle(ExcelStyleTemplate t) {
        WriteCellStyle style = new WriteCellStyle();
        style.setHorizontalAlignment(t.contentAlign());
        applyBorder(style);

        WriteFont font = new WriteFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 11);
        style.setWriteFont(font);
        return style;
    }

    private static void applyBorder(WriteCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
