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

    private final int colSplit;
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

    @Override
    public void afterSheetCreate(SheetWriteHandlerContext context) {
        context.getWriteSheetHolder().getSheet().createFreezePane(colSplit, rowSplit);
    }
}
