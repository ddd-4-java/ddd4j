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
 * \u5e8f\u5217\u53f7\u914d\u7f6e\u5c5e\u6027\uff08\u96ea\u82b1\u7b97\u6cd5\uff09\u3002
 *  <p>\u914d\u7f6e\u96ea\u82b1\u7b97\u6cd5 ID \u751f\u6210\u5668\u7684\u76f8\u5173\u53c2\u6570\uff0c\u5982\u662f\u5426\u4f7f\u7528\u7cfb\u7edf\u65f6\u949f\u3001\u5141\u8bb8\u65f6\u95f4\u56de\u62e8\u91cf\u7b49\u3002</p>
 */
@Data
/**
 * 序列号配置属性（雪花算法）。
 * <p>配置雪花算法 ID 生成器的相关参数，如是否使用系统时钟、允许时间回拨量等。</p>
 */
public class SequenceProperties {

    /** \u5e8f\u5217\u914d\u7f6e\u524d\u7f00\u3002 */
    public static final String PREFIX = "sequence";

    /**
     * 是否使用{@link SystemClock} 获取当前时间戳
     */
    private boolean useSystemClock;
    /**
     * 允许时间回拨的毫秒量,建议5ms
     */
    private Long timeOffset = 5L;
    /**
     * 限定一个随机上限，在不同毫秒下生成序号时，给定一个随机数，避免偶数问题，0表示无随机，上限不包括值本身。
     */
    private Long randomSequenceLimit;


}
