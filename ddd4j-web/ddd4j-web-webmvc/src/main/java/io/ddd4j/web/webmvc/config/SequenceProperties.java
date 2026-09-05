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
package io.ddd4j.web.webmvc.config;

import cn.hutool.core.date.SystemClock;
import lombok.Data;

/**
 * 序列号配置属性（雪花算法）。
 * <p>配置雪花算法 ID 生成器的相关参数，如是否使用系统时钟、允许时间回拨量等。</p>
 */
@Data
public class SequenceProperties {

    public static final String PREFIX = "sequence";

    /**
     * 是否使用 {@link SystemClock} 获取当前时间戳
     */
    private boolean useSystemClock;
    /**
     * 允许时间回拨的毫秒量，建议 5ms
     */
    private Long timeOffset = 5L;
    /**
     * 限定一个随机上限，在不同毫秒下生成序号时给定一个随机数，避免偶数问题，0 表示无随机，上限不包括值本身
     */
    private Long randomSequenceLimit;

}
