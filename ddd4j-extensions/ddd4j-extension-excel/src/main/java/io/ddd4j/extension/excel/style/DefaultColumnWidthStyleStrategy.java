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

import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;

/**
 * 默认列宽策略。
 *
 * <p>直接复用 easyexcel 内置的 {@link LongestMatchColumnWidthStyleStrategy}，
 * 该策略基于单元格内容长度自动适配列宽，覆盖表头与数据两种场景。
 *
 * <p>替换原 {@code CellWidthStyleStrategy}（其内部 HashMap 缓存以 sheetNo 为 key，
 * 多 sheet 场景会污染；easyexcel 官方实现已修复）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DefaultColumnWidthStyleStrategy extends LongestMatchColumnWidthStyleStrategy {

    /**
     * 默认构造（使用 easyexcel 默认的列宽上限 255）。
     */
    public DefaultColumnWidthStyleStrategy() {
        super();
    }
}
