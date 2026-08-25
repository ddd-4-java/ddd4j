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

import com.alibaba.excel.write.style.row.AbstractRowHeightStyleStrategy;
import org.apache.poi.ss.usermodel.Row;

/**
 * 默认行高策略。
 *
 * <p>替换原 {@code CellRowHeightStyleStrategy}：
 * <ul>
 *   <li>从包 {@code io.ddd4j.extension.excel.easyexcel.style} 下移到 {@code style}</li>
 *   <li>方法访问修饰符保持不变，与 easyexcel {@link AbstractRowHeightStyleStrategy} 一致</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DefaultRowHeightStyleStrategy extends AbstractRowHeightStyleStrategy {

    /**
     * 默认表头行高（1/20 磅）。
     */
    public static final short DEFAULT_HEADER_ROW_HEIGHT = 600;

    private final short headerRowHeight;

    /**
     * 默认构造（使用 {@link #DEFAULT_HEADER_ROW_HEIGHT}）。
     */
    public DefaultRowHeightStyleStrategy() {
        this(DEFAULT_HEADER_ROW_HEIGHT);
    }

    /**
     * 指定表头行高构造。
     *
     * @param headerRowHeight 表头行高（1/20 磅）
     */
    public DefaultRowHeightStyleStrategy(short headerRowHeight) {
        this.headerRowHeight = headerRowHeight;
    }

    @Override
    protected void setHeadColumnHeight(Row row, int relativeRowIndex) {
        if (relativeRowIndex == 0) {
            row.setHeight(headerRowHeight);
        }
    }

    @Override
    protected void setContentColumnHeight(Row row, int relativeRowIndex) {
        // 内容行使用 Excel 默认行高
    }
}
