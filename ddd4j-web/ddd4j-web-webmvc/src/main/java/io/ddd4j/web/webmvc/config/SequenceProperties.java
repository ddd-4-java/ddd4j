/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webmvc.config;

import cn.hutool.core.date.SystemClock;
import lombok.Data;

@Data
public class SequenceProperties {

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
