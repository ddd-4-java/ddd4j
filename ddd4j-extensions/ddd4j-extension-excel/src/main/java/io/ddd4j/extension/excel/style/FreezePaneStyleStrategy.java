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

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.handler.context.SheetWriteHandlerContext;

/**
 * 冻结表头策略。
 *
 * <p>注册为 {@link SheetWriteHandler}，在 sheet 创建完成后调用
 * {@code sheet.createFreezePane(colSplit, rowSplit)} 实现冻结。
 *
 * <p>典型用途：导出长列表时冻结首行表头，方便滚动查看。
 *
 * <pre>{@code
 * EasyExcel.write(out, OrderVO.class)
 *     .registerWriteHandler(new FreezePaneStyleStrategy(0, 1))  // 冻结首行
 *     .sheet("订单").doWrite(data);
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class FreezePaneStyleStrategy implements SheetWriteHandler {

    /** \u51bb\u7ed3\u7a97\u683c\u6240\u4fdd\u7559\u7684\u5de6\u4fa7\u5217\u6570\u3002 */
    private final int colSplit;
    /** \u51bb\u7ed3\u7a97\u683c\u6240\u4fdd\u7559\u7684\u9876\u90e8\u884c\u6570\u3002 */
    private final int rowSplit;

    /**
     * 默认冻结首行（colSplit=0, rowSplit=1）。
     */
    public FreezePaneStyleStrategy() {
        this(0, 1);
    }

    /**
     * 自定义冻结位置。
     *
     * @param colSplit 冻结线左侧列数（0 表示不冻结列）
     * @param rowSplit 冻结线上侧行数（1 表示冻结表头行）
     */
    public FreezePaneStyleStrategy(int colSplit, int rowSplit) {
        this.colSplit = colSplit;
        this.rowSplit = rowSplit;
    }

    /**
     * \u521b\u5efa\u5de5\u4f5c\u8868\u4e4b\u540e\u6309\u914d\u7f6e\u51bb\u7ed3\u5de6\u4fa7\u5217\u548c\u9876\u90e8\u884c\u3002
     * @param context \u5305\u542b\u65b0\u5efa\u5de5\u4f5c\u8868\u7684\u5199\u5165\u4e0a\u4e0b\u6587
     */
    @Override
    public void afterSheetCreate(SheetWriteHandlerContext context) {
        context.getWriteSheetHolder().getSheet().createFreezePane(colSplit, rowSplit);
    }
}
