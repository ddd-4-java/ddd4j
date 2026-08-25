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
package io.ddd4j.extension.monitor.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Markdown 消息体。was {@code MarkDownVO}。
 *
 * <p>钉钉与企微机器人均支持此消息类型，字段名以兼容两端 webhook 协议为前提：
 * <ul>
 *   <li>{@code title}：钉钉协议需要，可空</li>
 *   <li>{@code text}：钉钉协议需要的"普通文本模式"字段名</li>
 *   <li>{@code content}：企微协议要求的字段名（与 {@code text} 同义，二选一，由各通道决定使用）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Markdown {

    /**
     * Markdown 标题（钉钉）
     */
    private String title;
    /**
     * Markdown 正文内容（钉钉）
     */
    private String text;
    /**
     * Markdown 正文内容（企微）
     */
    private String content;
}
